package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Internal API 鉴权配置，集中绑定跨服务调用使用的共享 token。
 */
@ConfigurationProperties(prefix = "app.internal-api")
public record InternalApiProperties(String token) {
}
