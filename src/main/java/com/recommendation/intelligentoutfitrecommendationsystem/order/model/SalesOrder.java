package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单主表持久化模型。
 *
 * 该模型只保存订单归属、业务单号、支付状态和订单总额；商品名称、规格和成交单价
 * 必须固化在 OrderItem 中，避免商品目录后续变化污染历史订单。
 */
@Data
public class SalesOrder {

    private Long id;

    private String orderNo;

    private Long userId;

    private BigDecimal totalAmount;

    private String status;

    private LocalDateTime paidAt;

    private LocalDateTime closedAt;

    private String closeReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
