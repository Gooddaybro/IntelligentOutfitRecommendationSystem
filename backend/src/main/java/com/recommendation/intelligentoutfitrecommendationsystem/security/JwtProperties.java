package com.recommendation.intelligentoutfitrecommendationsystem.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属性，集中管理签名密钥和 access/refresh token 生命周期。
 */
@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;

    private long accessTokenSeconds = 1800;

    private long refreshTokenDays = 14;
}
