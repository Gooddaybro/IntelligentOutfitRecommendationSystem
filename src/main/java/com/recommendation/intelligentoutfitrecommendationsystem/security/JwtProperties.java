package com.recommendation.intelligentoutfitrecommendationsystem.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;

    private long accessTokenSeconds = 1800;

    private long refreshTokenDays = 14;
}
