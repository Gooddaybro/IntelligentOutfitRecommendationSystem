package com.recommendation.intelligentoutfitrecommendationsystem.conversation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentMerger;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper.ConversationMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatMessage;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatSession;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatDemandState;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 维护 AI 会话和消息历史。
 *
 * 所有读写都以 userId + threadId 做边界校验，避免前端或 AI 链路通过 threadId 访问其他用户会话。
 */
@Service
public class ConversationApplicationService {

    private static final String ACTIVE_STATUS = "active";
    private static final String SUCCEEDED_STATUS = "succeeded";

    private final ConversationMapper conversationMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemandIntentMerger demandIntentMerger = new DemandIntentMerger();

    public ConversationApplicationService(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public ConversationResponse createConversation(Long userId, String title) {
        ChatSession session = new ChatSession();
        session.setThreadId(newThreadId());
        session.setUserId(userId);
        session.setTitle(normalizeTitle(title));
        session.setStatus(ACTIVE_STATUS);
        conversationMapper.insertSession(session);
        return toConversationResponse(requireConversation(userId, session.getThreadId()));
    }

    public List<ConversationResponse> listConversations(Long userId) {
        return conversationMapper.findSessionsByUserId(userId).stream()
                .map(this::toConversationResponse)
                .toList();
    }

    public List<MessageResponse> getMessages(Long userId, String threadId) {
        ChatSession session = requireConversation(userId, threadId);
        return conversationMapper.findMessagesBySessionId(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional
    public void archiveConversation(Long userId, String threadId) {
        requireConversation(userId, threadId);
        conversationMapper.archiveSession(threadId, userId);
    }

    @Transactional
    public MessageResponse appendMessage(
            Long userId,
            String threadId,
            String role,
            String content,
            String requestId
    ) {
        if (isBlank(content)) {
            throw new BadRequestException("message content must not be blank");
        }
        ChatSession session = requireConversation(userId, threadId);
        // assistant-service 复用同一张消息表保存 user/assistant 消息，requestId 用于串起一次 AI 调用的日志和历史记录。
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setMessageStatus(SUCCEEDED_STATUS);
        message.setRequestId(requestId);
        conversationMapper.insertMessage(message);
        conversationMapper.touchSessionLastMessageAt(session.getId());
        return toMessageResponse(message);
    }

    @Transactional
    public DemandIntent applyDemandPatch(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            DemandIntentPatch patch,
            DemandIntent initialIntent
    ) {
        ChatSession session = requireConversation(userId, threadId);
        String replayJson = conversationMapper.findTransitionIntentJson(session.getId(), requestId);
        if (replayJson != null) {
            return readIntent(replayJson);
        }

        ChatDemandState state = conversationMapper.findDemandState(session.getId());
        DemandIntent effective = state == null
                ? initialIntent
                : demandIntentMerger.merge(readIntent(state.getEffectiveIntentJson()), patch);
        String effectiveJson = writeJson(effective);

        if (state == null) {
            ChatDemandState inserted = new ChatDemandState();
            inserted.setSessionId(session.getId());
            inserted.setStateVersion(0L);
            inserted.setEffectiveIntentJson(effectiveJson);
            inserted.setLastRequestId(requestId);
            conversationMapper.insertDemandState(inserted);
        } else if (conversationMapper.updateDemandState(
                session.getId(), state.getStateVersion(), effectiveJson, requestId) != 1) {
            throw new IllegalStateException("demand state changed concurrently");
        }

        String action = state == null && "initialize".equals(patch.action()) ? "initialize" : patch.action();
        conversationMapper.insertDemandTransition(
                session.getId(), messageId, requestId, action, writeJson(patch), effectiveJson);
        return effective;
    }

    /**
     * 验证当前用户拥有指定会话，不向模块外暴露持久化模型。
     *
     * @param userId 当前认证用户 ID
     * @param threadId 前端或 Assistant 提供的会话标识
     * @throws BadRequestException threadId 为空时抛出
     * @throws ResourceNotFoundException 会话不存在或不属于当前用户时抛出
     */
    public void assertOwned(Long userId, String threadId) {
        requireConversation(userId, threadId);
    }

    private ChatSession requireConversation(Long userId, String threadId) {
        if (isBlank(threadId)) {
            throw new BadRequestException("threadId must not be blank");
        }
        // 这里不提供只按 threadId 查询的方法，防止后续新增接口时绕过当前用户隔离。
        ChatSession session = conversationMapper.findSessionByThreadIdAndUserId(threadId, userId);
        if (session == null) {
            throw new ResourceNotFoundException("conversation not found: " + threadId);
        }
        return session;
    }

    private ConversationResponse toConversationResponse(ChatSession session) {
        return new ConversationResponse(
                session.getThreadId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getLastMessageAt()
        );
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getMessageStatus(),
                message.getRequestId(),
                message.getCreatedAt()
        );
    }

    private DemandIntent readIntent(String json) {
        try {
            return objectMapper.readValue(json, DemandIntent.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid stored demand intent", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize demand state", exception);
        }
    }

    private String newThreadId() {
        return "th_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String normalizeTitle(String title) {
        if (isBlank(title)) {
            return null;
        }
        return title.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
