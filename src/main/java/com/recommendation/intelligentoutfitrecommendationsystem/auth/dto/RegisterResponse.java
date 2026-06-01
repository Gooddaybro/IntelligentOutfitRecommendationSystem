package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

/**
 * 注册成功响应，返回前端创建账号后的稳定身份标识和账号状态。
 */
public record RegisterResponse(
        Long userId,
        String username,
        String status
) {
}
