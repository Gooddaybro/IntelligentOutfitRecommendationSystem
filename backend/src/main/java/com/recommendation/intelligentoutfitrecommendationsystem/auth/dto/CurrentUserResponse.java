package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

import java.util.List;

/**
 * 前端初始化登录态所需的当前账号信息。
 *
 * 画像、身体数据和穿衣偏好由 /api/me/** 单独维护，不混入账号响应。
 */
public record CurrentUserResponse(
        Long userId,
        String username,
        String status,
        List<String> roles
) {
}
