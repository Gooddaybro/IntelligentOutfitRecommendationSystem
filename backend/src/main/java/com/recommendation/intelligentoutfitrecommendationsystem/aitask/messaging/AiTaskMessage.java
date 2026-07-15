package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

/**
 * RabbitMQ 中的 v1 AI 任务事件信封，不携带知识正文、密钥或用户画像。
 */
public record AiTaskMessage(
        String eventId,
        String eventType,
        int schemaVersion,
        String taskId,
        String taskType,
        String occurredAt,
        String correlationId,
        String traceparent
) {
}
