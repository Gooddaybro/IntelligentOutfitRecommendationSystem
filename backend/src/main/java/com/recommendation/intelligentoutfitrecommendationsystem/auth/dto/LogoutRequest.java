package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登出接口入参，通过 refresh token 定位并撤销当前会话凭据。
 */
public record LogoutRequest(
        @NotBlank String refreshToken
) {
}
