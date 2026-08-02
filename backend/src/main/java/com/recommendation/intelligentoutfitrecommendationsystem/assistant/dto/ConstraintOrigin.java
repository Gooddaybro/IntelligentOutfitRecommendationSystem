package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Records the provenance needed to resolve conflicts without treating inferred values as user facts.
 */
public enum ConstraintOrigin {
    USER_EXPLICIT,
    PROFILE,
    SYSTEM_DERIVED,
    LEGACY_UNPROVENANCED
}
