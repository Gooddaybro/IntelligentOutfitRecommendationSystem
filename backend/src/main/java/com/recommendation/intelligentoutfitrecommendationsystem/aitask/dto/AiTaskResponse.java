package com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto;

/**
 * 管理员查看的 AI 任务快照，replayed 表示创建请求合并到了已有活动任务。
 */
public record AiTaskResponse(
        String taskId,
        String taskType,
        String status,
        int attemptCount,
        String failureCode,
        String failureSummary,
        String resultJson,
        boolean replayed
) {
}
