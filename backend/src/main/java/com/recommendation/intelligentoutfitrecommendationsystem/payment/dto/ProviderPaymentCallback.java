package com.recommendation.intelligentoutfitrecommendationsystem.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 已通过渠道签名校验的支付回调事实。
 *
 * 该 DTO 只表示服务商声称的支付结果；订单归属、金额匹配和最终状态变更仍由
 * {@code PaymentService} 重新读取 Java 数据库后决定。
 */
public record ProviderPaymentCallback(
        String paymentNo,
        String orderNo,
        BigDecimal amount,
        String status,
        String providerTradeNo,
        String transactionId,
        String providerPayload,
        LocalDateTime paidAt
) {
}
