package com.recommendation.intelligentoutfitrecommendationsystem.aitask.service;

/**
 * AI 长任务的持久化状态；数据库是状态事实源，RabbitMQ 只安排执行。
 */
public enum AiTaskStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    SUCCESS,
    FAILED
}
