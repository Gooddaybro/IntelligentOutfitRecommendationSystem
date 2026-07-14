package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

/**
 * 同一个幂等键被用于不同请求参数时抛出的业务冲突。
 *
 * 该异常区分参数格式错误和已存在购买意图冲突，使公开 API 可以稳定返回 HTTP 409。
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String message) {
        super(message);
    }
}
