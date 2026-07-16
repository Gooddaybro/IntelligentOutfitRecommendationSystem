package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Untrusted semantic parse candidate; Java must validate it before use. */
public record LlmDemandParseResponse(
        String schemaVersion,
        String action,
        LlmDemandSlots slots,
        Map<String, BigDecimal> slotConfidence,
        Map<String, List<SlotEvidence>> evidence,
        boolean needsClarification,
        String clarificationSlot,
        Object clarificationCandidateValue,
        String clarificationQuestion
) {
    public LlmDemandParseResponse(
            String schemaVersion,
            String action,
            LlmDemandSlots slots,
            Map<String, BigDecimal> slotConfidence,
            Map<String, List<SlotEvidence>> evidence,
            boolean needsClarification,
            String clarificationSlot,
            String clarificationQuestion
    ) {
        this(schemaVersion, action, slots, slotConfidence, evidence, needsClarification,
                clarificationSlot, null, clarificationQuestion);
    }
}
