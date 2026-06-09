package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌接口入参，用于滚动刷新 access token 和 refresh token。
 */
public record RefreshTokenRequest(
        @NotBlank String refreshToken
) {
}
