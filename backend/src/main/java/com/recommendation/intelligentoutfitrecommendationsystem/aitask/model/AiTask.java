package com.recommendation.intelligentoutfitrecommendationsystem.aitask.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 长任务持久化模型，承载全局唯一活动槽、租约及最终执行结果。
 */
@Data
public class AiTask {
    private Long id;
    private String taskId;
    private String taskType;
    private Long createdBy;
    private String status;
    private String activeSlot;
    private Integer attemptCount;
    private String workerId;
    private LocalDateTime leaseUntil;
    private Long version;
    private String failureCode;
    private String failureSummary;
    private String resultJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
