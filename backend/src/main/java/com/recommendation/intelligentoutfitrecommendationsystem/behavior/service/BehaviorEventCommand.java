package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import java.util.Map;

/**
 * 后端业务流程记录行为事实的命令对象。
 */
public record BehaviorEventCommand(
        String eventId,
        Long userId,
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
}
