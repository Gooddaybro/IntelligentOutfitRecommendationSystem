package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import org.springframework.stereotype.Component;

/**
 * 支付宝渠道骨架。
 *
 * Stage 6 不调用真实支付宝 SDK，而是返回待处理支付流水；支付成功必须等待签名回调
 * 通过 Java 后端校验后再进入统一交易状态变更路径。
 */
@Component
public class AlipayPaymentStrategy implements PaymentStrategy {

    @Override
    public String channel() {
        return "ALIPAY";
    }

    @Override
    public PaymentResult pay(PaymentRequestContext context) {
        return new PaymentResult(
                context.paymentNo(),
                context.channel(),
                "PENDING",
                null,
                null,
                "{\"channel\":\"ALIPAY\",\"paymentNo\":\"" + context.paymentNo() + "\"}",
                null
        );
    }
}
