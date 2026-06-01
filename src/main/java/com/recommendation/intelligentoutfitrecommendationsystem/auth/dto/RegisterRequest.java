package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册接口入参，集中约束账号标识和初始密码的公开 API 边界。
 */
public record RegisterRequest(
        @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[A-Za-z0-9_]+$")
        String username,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @Size(max = 32)
        String phone,

        @Email
        @Size(max = 128)
        String email
) {
}
