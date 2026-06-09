package com.recommendation.intelligentoutfitrecommendationsystem.auth.model;

import lombok.Data;

/**
 * 登录审计记录。
 *
 * 只记录认证结果和客户端上下文，不记录密码、access token 或 refresh token 明文。
 */
@Data
public class LoginLog {

    private Long userId;

    private String username;

    private Boolean success;

    private String failReason;

    private String ipAddress;

    private String userAgent;
}
