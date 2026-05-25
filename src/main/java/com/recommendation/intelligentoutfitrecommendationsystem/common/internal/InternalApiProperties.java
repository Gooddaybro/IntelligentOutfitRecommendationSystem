package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internal-api")
public record InternalApiProperties(String token) {
}
