package com.recommendation.intelligentoutfitrecommendationsystem.behavior.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为事件持久化模型。
 *
 * 行为事件服务于推荐反馈和用户偏好沉淀，不是订单、支付或库存事实源。
 */
@Data
public class BehaviorEvent {
    private Long id;
    private String eventId;
    private Long userId;
    private String eventType;
    private String source;
    private Long spuId;
    private Long skuId;
    private String threadId;
    private String requestId;
    private String orderNo;
    private String recommendationId;
    private Integer quantity;
    private LocalDateTime eventTime;
    private String metadataJson;
    private LocalDateTime createdAt;
}
