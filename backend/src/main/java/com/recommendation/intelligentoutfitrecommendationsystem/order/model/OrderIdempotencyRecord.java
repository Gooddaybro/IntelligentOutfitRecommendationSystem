package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单创建幂等记录的持久化模型。
 *
 * 记录只保存购买意图摘要和成功订单引用，不复制订单响应或交易事实；库存和订单状态
 * 始终以 MySQL 业务表为准。
 */
@Data
public class OrderIdempotencyRecord {
    private Long id;
    private Long userId;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long orderId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
