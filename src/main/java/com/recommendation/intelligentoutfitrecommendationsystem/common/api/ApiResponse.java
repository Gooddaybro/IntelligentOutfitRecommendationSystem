package com.recommendation.intelligentoutfitrecommendationsystem.common.api;

/**
 * 统一 API 响应包装，保证前端和 internal 调用方读取一致的成功与错误结构。
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, "ok");
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
