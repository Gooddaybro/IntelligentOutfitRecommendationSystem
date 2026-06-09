package com.recommendation.intelligentoutfitrecommendationsystem.common.api;

/**
 * 简化错误响应结构，供不需要 data 字段的错误场景复用。
 */
public record ErrorResponse(
        String errorCode,
        String message
) {
}
