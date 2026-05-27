package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

/**
 * 登录或刷新令牌时记录的客户端上下文。
 *
 * 这些字段不参与认证判断，主要用于 refresh_token 存档、登录审计和后续多端登录控制。
 */
public record AuthClientContext(
        String deviceId,
        String userAgent,
        String ipAddress
) {
}
