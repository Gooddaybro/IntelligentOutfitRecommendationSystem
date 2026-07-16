package com.recommendation.intelligentoutfitrecommendationsystem.conversation.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper.ConversationMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatDemandState;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatSession;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.UnaryOperator;

/** Conversation-owned JSON snapshot store; it deliberately knows nothing about assistant DTOs. */
@Service
public class ConversationDemandStateStore {

    private final ConversationMapper conversationMapper;

    public ConversationDemandStateStore(ConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public String apply(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            String action,
            String patchJson,
            String initialIntentJson,
            UnaryOperator<String> mergeStoredIntent
    ) {
        ChatSession session = conversationMapper.findSessionByThreadIdAndUserId(threadId, userId);
        if (session == null) {
            throw new ResourceNotFoundException("conversation not found: " + threadId);
        }
        ChatDemandState replay = conversationMapper.findTransitionState(session.getId(), requestId);
        if (replay != null) {
            return replay.getEffectiveIntentJson();
        }

        ChatDemandState state = conversationMapper.findDemandState(session.getId());
        String effectiveJson = state == null
                ? initialIntentJson
                : mergeStoredIntent.apply(state.getEffectiveIntentJson());
        if (state == null) {
            insertState(session.getId(), requestId, effectiveJson);
        } else if (conversationMapper.updateDemandState(
                session.getId(), state.getStateVersion(), effectiveJson,
                state.getPendingClarificationJson(), requestId) != 1) {
            replay = conversationMapper.findTransitionState(session.getId(), requestId);
            if (replay != null) {
                return replay.getEffectiveIntentJson();
            }
            throw new IllegalStateException("demand state changed concurrently");
        }

        conversationMapper.insertDemandTransition(
                session.getId(), messageId, requestId, action, patchJson, effectiveJson,
                state == null ? null : state.getPendingClarificationJson());
        return effectiveJson;
    }

    public ConversationDemandStateSnapshot read(Long userId, String threadId) {
        ChatSession session = conversationMapper.findSessionByThreadIdAndUserId(threadId, userId);
        if (session == null) {
            throw new ResourceNotFoundException("conversation not found: " + threadId);
        }
        ChatDemandState state = conversationMapper.findDemandState(session.getId());
        return state == null ? null : snapshot(state);
    }

    @Transactional
    public ConversationDemandStateSnapshot transition(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            String action,
            String patchJson,
            String initialIntentJson,
            UnaryOperator<ConversationDemandStateSnapshot> mutation
    ) {
        ChatSession session = conversationMapper.findSessionByThreadIdAndUserId(threadId, userId);
        if (session == null) {
            throw new ResourceNotFoundException("conversation not found: " + threadId);
        }
        ChatDemandState replay = conversationMapper.findTransitionState(session.getId(), requestId);
        if (replay != null) {
            return snapshot(replay);
        }
        ChatDemandState state = conversationMapper.findDemandState(session.getId());
        ConversationDemandStateSnapshot base = state == null
                ? new ConversationDemandStateSnapshot(initialIntentJson, null) : snapshot(state);
        ConversationDemandStateSnapshot result = mutation.apply(base);
        if (state == null) {
            insertState(session.getId(), requestId, result.effectiveIntentJson(), result.pendingClarificationJson());
        } else if (conversationMapper.updateDemandState(session.getId(), state.getStateVersion(),
                result.effectiveIntentJson(), result.pendingClarificationJson(), requestId) != 1) {
            replay = conversationMapper.findTransitionState(session.getId(), requestId);
            if (replay != null) {
                return snapshot(replay);
            }
            throw new IllegalStateException("demand state changed concurrently");
        }
        conversationMapper.insertDemandTransition(session.getId(), messageId, requestId, action,
                patchJson, result.effectiveIntentJson(), result.pendingClarificationJson());
        return result;
    }

    private void insertState(Long sessionId, String requestId, String effectiveJson) {
        insertState(sessionId, requestId, effectiveJson, null);
    }

    private void insertState(Long sessionId, String requestId, String effectiveJson, String pendingJson) {
        ChatDemandState state = new ChatDemandState();
        state.setSessionId(sessionId);
        state.setStateVersion(0L);
        state.setEffectiveIntentJson(effectiveJson);
        state.setPendingClarificationJson(pendingJson);
        state.setLastRequestId(requestId);
        conversationMapper.insertDemandState(state);
    }

    private ConversationDemandStateSnapshot snapshot(ChatDemandState state) {
        return new ConversationDemandStateSnapshot(
                state.getEffectiveIntentJson(), state.getPendingClarificationJson());
    }
}
