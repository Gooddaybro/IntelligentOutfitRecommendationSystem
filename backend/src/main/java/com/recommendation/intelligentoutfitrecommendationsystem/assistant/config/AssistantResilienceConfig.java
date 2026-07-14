package com.recommendation.intelligentoutfitrecommendationsystem.assistant.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Python AI 依赖的故障隔离配置。
 *
 * CircuitBreaker 只保护 Java 到 Python 的远程调用，不影响商品、订单、库存和支付等本地事实能力。
 */
@Configuration
public class AssistantResilienceConfig {

    @Bean
    public CircuitBreaker pythonAssistantCircuitBreaker(
            @Value("${app.ai.circuit-breaker.sliding-window-size:4}") int slidingWindowSize,
            @Value("${app.ai.circuit-breaker.minimum-number-of-calls:4}") int minimumNumberOfCalls,
            @Value("${app.ai.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${app.ai.circuit-breaker.wait-duration-ms:10000}") long waitDurationMs,
            @Value("${app.ai.circuit-breaker.half-open-permitted-calls:1}") int halfOpenPermittedCalls
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationMs))
                .permittedNumberOfCallsInHalfOpenState(halfOpenPermittedCalls)
                .build();
        return CircuitBreaker.of("python-assistant", config);
    }
}
