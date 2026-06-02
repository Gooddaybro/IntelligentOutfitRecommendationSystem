package com.recommendation.intelligentoutfitrecommendationsystem.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付结果响应契约。
 *
 * 该 DTO 暴露 mock 支付的业务流水和状态，不暴露 payment 自增主键；重复支付已支付订单时
 * 返回同一份成功流水，供前端做幂等展示。
 */
public record PaymentResponse(
        String paymentNo,
        String orderNo,
        BigDecimal amount,
        String channel,
        String status,
        String transactionId,
        LocalDateTime paidAt
) {
}
