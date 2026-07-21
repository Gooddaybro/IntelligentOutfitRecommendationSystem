package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Classifies whether an effective demand is usable, unusual, priority-resolved, or requires clarification.
 * The status is diagnostic only and does not authorize deleting user-explicit constraints.
 */
public enum ConstraintConflictStatus {
    VALID,
    VALID_UNCOMMON_COMBINATION,
    RESOLVED_BY_PRIORITY,
    UNRESOLVED_HARD_CONFLICT
}
