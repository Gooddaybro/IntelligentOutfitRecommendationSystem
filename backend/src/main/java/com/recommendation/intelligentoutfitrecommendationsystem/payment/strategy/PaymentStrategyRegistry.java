package com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 根据渠道解析支付策略。
 *
 * <p>在订单或库存状态发生任何变更之前，系统会拒绝不支持的渠道；
 * 这种做法既保证了公共支付 API 的可扩展性，又避免了前端传入任意渠道名称。</p>
 */
@Component
public class PaymentStrategyRegistry {

    private final Map<String, PaymentStrategy> strategies;

    public PaymentStrategyRegistry(List<PaymentStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::channel, Function.identity()));
    }

    public PaymentStrategy getRequired(String channel) {
        PaymentStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new BadRequestException("unsupported payment channel: " + channel);
        }
        return strategy;
    }
}
