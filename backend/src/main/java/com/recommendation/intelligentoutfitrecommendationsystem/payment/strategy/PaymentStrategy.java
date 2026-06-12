package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

/**
 * 针对特定渠道的支付边界。
 *
 * <p>实现类可以调用外部服务提供商、构建重定向载荷或模拟支付，
 * 但不得直接更新订单或库存。状态变更由 {@code PaymentService} 统一处理，
 * 从而确保所有渠道均采用相同的幂等事务处理路径。</p>
 */
public interface PaymentStrategy {

    String channel();

    PaymentResult pay(PaymentRequestContext context);
}
