package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

/**
 * 请求超过限流阈值时抛出的异常，统一映射为 HTTP 429。
 */
public class RateLimitExceededException extends RuntimeException {
    public RateLimitExceededException(String message) {
        super(message);
    }
}
