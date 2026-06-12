package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import java.math.BigDecimal;

/**
 * 传递给渠道策略的不可变支付信息。
 *
 * <p>在创建此对象之前，金额和订单归属信息已由 Java 从数据库加载。
 * 前端请求绝不会提供金额或用户 ID。</p>
 */
public record PaymentRequestContext(
        String paymentNo,
        String orderNo,
        Long userId,
        BigDecimal amount,
        String channel
) {
}
