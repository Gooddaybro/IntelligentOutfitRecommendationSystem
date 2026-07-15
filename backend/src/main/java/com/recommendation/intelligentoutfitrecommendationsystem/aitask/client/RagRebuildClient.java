package com.recommendation.intelligentoutfitrecommendationsystem.aitask.client;

/**
 * Java Worker 调用 Python 索引重建的边界，避免消息消费逻辑依赖具体 HTTP 实现。
 */
public interface RagRebuildClient {

    RagRebuildResult rebuild(String taskId, String correlationId, String traceparent);
}
