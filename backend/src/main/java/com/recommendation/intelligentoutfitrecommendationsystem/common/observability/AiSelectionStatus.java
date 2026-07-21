package com.recommendation.intelligentoutfitrecommendationsystem.common.observability;

/**
 * Bounded metric status mirroring the assistant API decision states without coupling common metrics to that module.
 */
public enum AiSelectionStatus {
    STRONG_MATCH,
    PARTIAL_MATCH,
    BROWSE_FALLBACK,
    EMPTY,
    FAILED
}
