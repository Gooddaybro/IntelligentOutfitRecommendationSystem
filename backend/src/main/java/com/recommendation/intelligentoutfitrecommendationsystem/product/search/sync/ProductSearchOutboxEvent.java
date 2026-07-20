package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 与商品事务一起持久化、等待可靠发布的搜索同步事件。
 */
@Data
public class ProductSearchOutboxEvent {
    private Long id;
    private String eventId;
    private Long spuId;
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
