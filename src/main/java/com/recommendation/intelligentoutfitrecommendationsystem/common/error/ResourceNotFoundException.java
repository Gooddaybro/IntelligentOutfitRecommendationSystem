package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

/**
 * 业务资源不存在时抛出的异常，统一映射为 HTTP 404。
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
