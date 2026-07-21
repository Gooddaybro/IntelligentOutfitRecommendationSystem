package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** Java 拥有的最终推荐状态，以及通过商品成员资格、证据和角色校验的商品。 */
public record RecommendationDecision(
        RecommendationStatus recommendationStatus,
        List<AssistantRecommendationItem> recommendedItems,
        int discardedReferences
) {
    public RecommendationDecision {
        recommendedItems = recommendedItems == null ? List.of() : List.copyOf(recommendedItems);
    }
}
