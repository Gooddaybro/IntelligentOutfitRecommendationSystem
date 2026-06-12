package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 模拟服务提供方成功结果的开发用支付策略。
 *
 * <p>该类将模拟行为封装在与真实服务提供方相同的通道边界之后，
 * 使得测试和前端流程能够在无需外部凭证的情况下，验证生产环境中的交易数据结构。</p>
 */
@Component
public class MockPaymentStrategy implements PaymentStrategy {

    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public PaymentResult pay(PaymentRequestContext context) {
        LocalDateTime paidAt = LocalDateTime.now();
        String transactionId = UUID.randomUUID().toString();
        return new PaymentResult(
                context.paymentNo(),
                context.channel(),
                "SUCCESS",
                "MOCK-" + transactionId,
                transactionId,
                "{\"channel\":\"MOCK\"}",
                paidAt
        );
    }
}
