package com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.Map;

/**
 * 前端上报推荐交互事件的请求体。
 *
 * eventId 可由前端生成用于去重；为空时后端生成一次性事件 ID。
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

        Map<String, Object> metadata
) {
}
