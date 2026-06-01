package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

/**
 * 请求参数或业务前置条件不合法时抛出的异常，统一映射为 HTTP 400。
 */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
