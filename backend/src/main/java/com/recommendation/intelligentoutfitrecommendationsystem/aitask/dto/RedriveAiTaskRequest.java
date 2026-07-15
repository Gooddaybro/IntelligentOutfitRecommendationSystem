package com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto;

import jakarta.validation.constraints.Size;

/**
 * 管理员重放失败 AI 任务时提交的可选审计原因。
 */
public record RedriveAiTaskRequest(@Size(max = 500) String reason) {
}
