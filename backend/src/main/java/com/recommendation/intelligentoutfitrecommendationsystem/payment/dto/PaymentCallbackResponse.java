package com.recommendation.intelligentoutfitrecommendationsystem.payment.dto;

/**
 * 返回给支付回调发送方的响应。
 *
 * <p>该响应仅确认 Java 后端已收到回调。它不会泄露订单归属、支付金额或状态是否发生了变更。</p>
 */
public record PaymentCallbackResponse(
        boolean received,
        String channel
) {
}
