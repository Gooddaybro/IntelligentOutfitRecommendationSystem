package com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * 前端上报推荐交互事件的请求体。
 *
 * eventId 可由前端生成用于去重；recommendationId 存在时，后端会校验它属于当前用户且包含当前最终推荐商品。
 */
public record BehaviorEventRequest(
        String eventId,

        @NotBlank
        String eventType,

        String source,

        @Positive
        Long spuId,

        @Positive
        Long skuId,

        String threadId,
        String requestId,
        String orderNo,

        @Positive
        Integer quantity,

        Map<String, Object> metadata,
        String recommendationId
) {
    public BehaviorEventRequest(
            String eventId,
            String eventType,
            String source,
            Long spuId,
            Long skuId,
            String threadId,
            String requestId,
            String orderNo,
            Integer quantity,
            Map<String, Object> metadata
    ) {
        this(eventId, eventType, source, spuId, skuId, threadId, requestId, orderNo, quantity, metadata, null);
    }
}
