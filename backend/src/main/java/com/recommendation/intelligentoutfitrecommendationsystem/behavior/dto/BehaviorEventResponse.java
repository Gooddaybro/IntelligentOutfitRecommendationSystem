package com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto;

/**
 * 行为事件写入响应。
 *
 * 对重复事件同样返回成功，调用方只需要拿 eventId 做链路追踪。
 */
public record BehaviorEventResponse(
        String eventId
) {
}
