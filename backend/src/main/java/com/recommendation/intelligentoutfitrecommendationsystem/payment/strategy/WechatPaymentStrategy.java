package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import org.springframework.stereotype.Component;

/**
 * 微信支付渠道骨架。
 *
 * 该策略只创建待处理支付尝试，不直接确认订单；真实支付成功由公开回调入口完成签名校验后
 * 交给 {@code PaymentService} 的统一成功确认事务。
 */
@Component
public class WechatPaymentStrategy implements PaymentStrategy {

    @Override
    public String channel() {
        return "WECHAT";
    }

    @Override
    public PaymentResult pay(PaymentRequestContext context) {
        return new PaymentResult(
                context.paymentNo(),
                context.channel(),
                "PENDING",
                null,
                null,
                "{\"channel\":\"WECHAT\",\"paymentNo\":\"" + context.paymentNo() + "\"}",
                null
        );
    }
}
