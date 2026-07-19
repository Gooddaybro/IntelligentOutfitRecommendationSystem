package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * AI 导购接口返回给前端的同步结果。
 *
 * recommendedSpuIds 保持旧版排序引用；recommendedItems 承载已核验的强推荐理由；
 * mentionedItems 仅表示回答中提及且已精确绑定到本轮 Java 可售候选的商品。
 * 商品名称、价格和库存仍以 Java 商品库为准，mentionedItems 不产生推荐归因。
 */
public record AssistantChatResponse(
        String threadId,
        String answer,
        List<Long> recommendedSpuIds,
        List<AssistantRecommendationItem> recommendedItems,
        List<AssistantMentionedItem> mentionedItems,
        int candidatesCount,
        DemandIntent resolvedIntent,
        String recommendationStatus,
        String recommendationId
) {
    public AssistantChatResponse(
            String threadId,
            String answer,
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            int candidatesCount,
            DemandIntent resolvedIntent,
            String recommendationStatus
    ) {
        this(threadId, answer, recommendedSpuIds, recommendedItems, List.of(), candidatesCount,
                resolvedIntent, recommendationStatus, null);
    }

    public AssistantChatResponse(
            String threadId,
            String answer,
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            int candidatesCount
    ) {
        this(threadId, answer, recommendedSpuIds, recommendedItems, List.of(), candidatesCount,
                null, recommendedItems == null || recommendedItems.isEmpty() ? "WEAK_FALLBACK" : "STRONG_MATCH", null);
    }
}
