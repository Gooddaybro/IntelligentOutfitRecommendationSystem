package com.recommendation.intelligentoutfitrecommendationsystem.conversation.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper.ConversationMapper;
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
        String replay = conversationMapper.findTransitionIntentJson(session.getId(), requestId);
        if (replay != null) {
            return replay;
        }

        ChatDemandState state = conversationMapper.findDemandState(session.getId());
        String effectiveJson = state == null
                ? initialIntentJson
                : mergeStoredIntent.apply(state.getEffectiveIntentJson());
        if (state == null) {
            insertState(session.getId(), requestId, effectiveJson);
        } else if (conversationMapper.updateDemandState(
                session.getId(), state.getStateVersion(), effectiveJson, requestId) != 1) {
            state = conversationMapper.findDemandState(session.getId());
            effectiveJson = mergeStoredIntent.apply(state.getEffectiveIntentJson());
            if (conversationMapper.updateDemandState(
                    session.getId(), state.getStateVersion(), effectiveJson, requestId) != 1) {
                throw new IllegalStateException("demand state changed concurrently");
            }
        }

        conversationMapper.insertDemandTransition(
                session.getId(), messageId, requestId, action, patchJson, effectiveJson);
        return effectiveJson;
    }

    private void insertState(Long sessionId, String requestId, String effectiveJson) {
        ChatDemandState state = new ChatDemandState();
        state.setSessionId(sessionId);
        state.setStateVersion(0L);
        state.setEffectiveIntentJson(effectiveJson);
        state.setLastRequestId(requestId);
        conversationMapper.insertDemandState(state);
    }
}
