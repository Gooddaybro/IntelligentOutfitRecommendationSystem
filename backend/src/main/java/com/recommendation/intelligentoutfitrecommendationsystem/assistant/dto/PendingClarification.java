package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;

/** A candidate hard condition kept outside the effective SQL demand until the user confirms it. */
public record PendingClarification(
        String slot,
        Object candidateValue,
        BigDecimal confidence,
        String question,
        String rawText
) {
}
