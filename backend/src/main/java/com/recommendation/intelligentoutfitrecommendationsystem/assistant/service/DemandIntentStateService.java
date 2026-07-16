package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the assistant-specific state machine while reusing conversation persistence. */
@Service
public class DemandIntentStateService {

    private final ConversationDemandStateStore stateStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemandIntentMerger merger = new DemandIntentMerger();

    public DemandIntentStateService(ConversationDemandStateStore stateStore) {
        this.stateStore = stateStore;
    }

    @Transactional
    public DemandIntent apply(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            DemandIntentPatch patch,
            DemandIntent initialIntent
    ) {
        String effectiveJson = stateStore.apply(
                userId,
                threadId,
                requestId,
                messageId,
                patch.action(),
                writeJson(patch),
                writeJson(initialIntent),
                storedJson -> writeJson(merger.merge(readIntent(storedJson), patch))
        );
        return readIntent(effectiveJson);
    }

    public DemandIntentStateSnapshot read(Long userId, String threadId) {
        ConversationDemandStateSnapshot stored = stateStore.read(userId, threadId);
        if (stored == null) {
            return null;
        }
        return new DemandIntentStateSnapshot(
                readIntent(stored.effectiveIntentJson()), readPending(stored.pendingClarificationJson()));
    }

    @Transactional
    public DemandIntentStateSnapshot applyResolution(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            DemandIntentPatch deterministicPatch,
            DemandIntentPatch semanticPatch,
            PendingClarification pending,
            DemandIntent initialIntent
    ) {
        String action = pending == null ? "merge" : "clarify";
        return applyResolution(userId, threadId, requestId, messageId, action,
                deterministicPatch, semanticPatch, pending, initialIntent);
    }

    /** Atomically apply one explicit demand-state transition and preserve its audit action. */
    @Transactional
    public DemandIntentStateSnapshot applyResolution(
            Long userId,
            String threadId,
            String requestId,
            Long messageId,
            String action,
            DemandIntentPatch deterministicPatch,
            DemandIntentPatch semanticPatch,
            PendingClarification pending,
            DemandIntent initialIntent
    ) {
        if (!java.util.Set.of("merge", "clarify", "confirm", "cancel_clarify").contains(action)) {
            throw new IllegalArgumentException("unsupported demand transition action");
        }
        ConversationDemandStateSnapshot stored = stateStore.transition(
                userId, threadId, requestId, messageId, action,
                writeJson(semanticPatch == null ? deterministicPatch : semanticPatch),
                writeJson(initialIntent),
                current -> {
                    if ("cancel_clarify".equals(action)) {
                        return new ConversationDemandStateSnapshot(current.effectiveIntentJson(), null);
                    }
                    DemandIntent intent = readIntent(current.effectiveIntentJson());
                    if (deterministicPatch != null) {
                        intent = merger.merge(intent, deterministicPatch);
                    }
                    if (semanticPatch != null) {
                        intent = merger.merge(intent, semanticPatch);
                    }
                    return new ConversationDemandStateSnapshot(
                            writeJson(intent), pending == null ? null : writeJson(pending));
                }
        );
        return new DemandIntentStateSnapshot(
                readIntent(stored.effectiveIntentJson()), readPending(stored.pendingClarificationJson()));
    }

    private PendingClarification readPending(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, PendingClarification.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("invalid stored pending clarification", exception);
        }
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
}
