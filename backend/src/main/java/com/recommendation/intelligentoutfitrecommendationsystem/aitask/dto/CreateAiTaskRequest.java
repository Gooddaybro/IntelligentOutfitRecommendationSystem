package com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskType;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员创建 AI 长任务的公开请求；MVP 只接受 RAG_REBUILD。
 */
public record CreateAiTaskRequest(@NotNull AiTaskType taskType) {
}
