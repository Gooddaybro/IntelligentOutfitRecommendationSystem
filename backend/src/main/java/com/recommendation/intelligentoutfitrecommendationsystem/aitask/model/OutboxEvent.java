package com.recommendation.intelligentoutfitrecommendationsystem.aitask.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 与业务任务同事务写入的待发布事件，Publisher Confirm 成功前不得标记为已发布。
 */
@Data
public class OutboxEvent {
    private Long id;
    private String eventId;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private Integer schemaVersion;
    private String payload;
    private String status;
    private LocalDateTime availableAt;
    private String claimedBy;
    private LocalDateTime claimUntil;
    private Integer publishAttempts;
    private LocalDateTime publishedAt;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
