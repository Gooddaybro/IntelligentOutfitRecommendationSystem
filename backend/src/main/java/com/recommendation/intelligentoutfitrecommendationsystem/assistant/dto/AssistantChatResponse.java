package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * AI 导购接口返回给前端的同步结果。
 *
 * recommendedSpuIds 保持旧版排序引用；recommendedItems 承载可展示推荐理由。
 * 商品名称、价格和库存仍以 Java 商品库为准。
 */
public record AssistantChatResponse(
        String threadId,
        String answer,
        List<Long> recommendedSpuIds,
        List<AssistantRecommendationItem> recommendedItems,
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
        this(threadId, answer, recommendedSpuIds, recommendedItems, candidatesCount,
                resolvedIntent, recommendationStatus, null);
    }

    public AssistantChatResponse(
            String threadId,
            String answer,
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            int candidatesCount
    ) {
        this(threadId, answer, recommendedSpuIds, recommendedItems, candidatesCount,
                null, recommendedItems == null || recommendedItems.isEmpty() ? "WEAK_FALLBACK" : "STRONG_MATCH", null);
    }
}
