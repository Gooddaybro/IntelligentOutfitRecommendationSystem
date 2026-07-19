package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * Java 拥有最终解释权的推荐决策。
 *
 * @param recommendationStatus 本轮推荐状态
 * @param mentionedItems 已精确绑定到 Java 可售候选、可供展示并发起交易操作的对话提及商品；交易接口仍会重新校验
 * @param recommendedItems 在提及商品基础上进一步通过证据核验的强推荐商品
 * @param discardedReferences 因商品不存在、不可售、重复或证据无效而未进入强推荐归因的引用数；身份有效的引用仍可保留为普通提及
 */
public record RecommendationDecision(
        String recommendationStatus,
        List<AssistantMentionedItem> mentionedItems,
        List<AssistantRecommendationItem> recommendedItems,
        int discardedReferences
) {
    public RecommendationDecision {
        mentionedItems = mentionedItems == null ? List.of() : List.copyOf(mentionedItems);
        recommendedItems = recommendedItems == null ? List.of() : List.copyOf(recommendedItems);
    }
}
