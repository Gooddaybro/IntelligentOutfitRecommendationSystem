package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

/**
 * 登录或刷新成功后的双 Token 响应。
 *
 * accessToken 用于短期接口鉴权，refreshToken 只在客户端保存明文，服务端只保存哈希。
 */
public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
}
