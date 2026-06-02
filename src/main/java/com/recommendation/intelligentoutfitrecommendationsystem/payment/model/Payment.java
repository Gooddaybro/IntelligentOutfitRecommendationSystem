package com.recommendation.intelligentoutfitrecommendationsystem.payment.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付流水持久化模型。
 *
 * 该模型保存 mock 支付成功记录，并为未来真实支付渠道的外部交易号、幂等查询和审计
 * 保留稳定的数据边界；订单状态仍由 sales_order 维护。
 */
@Data
public class Payment {

    private Long id;

    private String paymentNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private BigDecimal amount;

    private String channel;

    private String status;

    private String transactionId;

    private LocalDateTime paidAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
