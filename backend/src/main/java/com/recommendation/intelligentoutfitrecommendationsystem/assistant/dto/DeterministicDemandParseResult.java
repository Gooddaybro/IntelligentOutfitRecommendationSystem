package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** Deterministic single-turn parse plus metadata used to decide whether semantic completion is needed. */
public record DeterministicDemandParseResult(
        DemandIntentPatch deterministicPatch,
        List<String> lockedSlots,
        List<String> matchedFragments,
        String unresolvedText,
        boolean hasShoppingSignal
) {
    public DeterministicDemandParseResult {
        lockedSlots = lockedSlots == null ? List.of() : List.copyOf(lockedSlots);
        matchedFragments = matchedFragments == null ? List.of() : List.copyOf(matchedFragments);
        unresolvedText = unresolvedText == null ? "" : unresolvedText.trim();
    }
}
