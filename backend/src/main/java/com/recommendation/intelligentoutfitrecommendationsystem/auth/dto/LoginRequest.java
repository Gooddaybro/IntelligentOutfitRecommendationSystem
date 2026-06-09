package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录接口入参，承载账号密码登录所需的最小凭据。
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
