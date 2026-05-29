package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * AI 导购接口返回给前端的同步结果。
 *
 * recommendedSpuIds 只保存商品引用，商品详情仍以 Java 商品库为准。
 */
public record AssistantChatResponse(
        String threadId,
        String answer,
        List<Long> recommendedSpuIds,
        int candidatesCount
) {
}
