package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * Minimal structured diagnosis produced after merge and derivation.
 * It stays outside {@link EffectiveDemand} so recommendation constraints remain free of workflow state.
 */
public record ConstraintConflictResult(
        ConstraintConflictStatus status,
        List<String> conflictingFields,
        String reason
) {
    public ConstraintConflictResult {
        if (status == null) {
            throw new IllegalArgumentException("conflict status is required");
        }
        conflictingFields = conflictingFields == null ? List.of() : List.copyOf(conflictingFields);
        reason = reason == null ? "" : reason;
    }
}
