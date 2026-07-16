package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/** Java-owned decision after validating an untrusted LLM response. */
public record ValidatedDemandParseResult(
        DemandIntentPatch patch,
        PendingClarification pendingClarification
) {
    public static ValidatedDemandParseResult rejected() {
        return new ValidatedDemandParseResult(null, null);
    }
}
