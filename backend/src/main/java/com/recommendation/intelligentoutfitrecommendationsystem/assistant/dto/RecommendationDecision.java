package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** Java-owned final recommendation state and the strong-match items that passed validation. */
public record RecommendationDecision(
        String recommendationStatus,
        List<AssistantRecommendationItem> recommendedItems,
        int discardedReferences
) {
    public RecommendationDecision {
        recommendedItems = recommendedItems == null ? List.of() : List.copyOf(recommendedItems);
    }
}
