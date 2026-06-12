package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import java.time.LocalDateTime;

/**
 * 由支付策略返回的提供方结果。
 *
 * <p>对于模拟支付，结果可立即显示为成功。对于实际支付提供方，初始响应可能为“待处理”状态，需依赖后续的验证回调来确认支付成功。</p>
 */
public record PaymentResult(
        String paymentNo,
        String channel,
        String status,
        String providerTradeNo,
        String transactionId,
        String providerPayload,
        LocalDateTime paidAt
) {
}
