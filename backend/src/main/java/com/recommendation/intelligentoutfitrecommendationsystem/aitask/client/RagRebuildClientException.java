package com.recommendation.intelligentoutfitrecommendationsystem.aitask.client;

/**
 * Python 重建失败分类，retryable 只表达是否可进入固定的有限重试链路。
 */
public class RagRebuildClientException extends RuntimeException {

    private final String code;
    private final boolean retryable;

    public RagRebuildClientException(String code, boolean retryable, String message) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public RagRebuildClientException(String code, boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
