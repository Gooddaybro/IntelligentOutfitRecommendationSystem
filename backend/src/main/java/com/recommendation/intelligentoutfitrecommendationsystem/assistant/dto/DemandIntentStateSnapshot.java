package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/** Typed assistant view of the conversation-owned JSON demand state. */
public record DemandIntentStateSnapshot(
        DemandIntent effectiveIntent,
        PendingClarification pendingClarification
) {
}
