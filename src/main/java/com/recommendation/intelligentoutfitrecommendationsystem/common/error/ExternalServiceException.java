package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

/**
 * 外部服务调用失败时抛出的异常，当前主要用于隔离 Python AI 服务故障。
 */
public class ExternalServiceException extends RuntimeException {
    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
