package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;

/** A candidate hard condition kept outside the effective SQL demand until the user confirms it. */
public record PendingClarification(
        String slot,
        Object candidateValue,
        BigDecimal confidence,
        String question,
        String rawText,
        String sourceRequestId
) {
    public PendingClarification(
            String slot,
            Object candidateValue,
            BigDecimal confidence,
            String question,
            String rawText
    ) {
        this(slot, candidateValue, confidence, question, rawText, null);
    }

    public PendingClarification withSourceRequestId(String requestId) {
        return new PendingClarification(slot, candidateValue, confidence, question, rawText, requestId);
    }
}
