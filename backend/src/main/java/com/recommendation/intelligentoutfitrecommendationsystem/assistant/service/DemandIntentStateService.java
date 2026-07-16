package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
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
