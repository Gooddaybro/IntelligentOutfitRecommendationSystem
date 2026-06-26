package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存 TTL 配置。
 *
 * TTL 统一放在配置类中，避免业务 Service 写死过期时间；随机抖动用于降低大量缓存
 * 同时过期后集中回源 MySQL 的风险。
 */
@ConfigurationProperties(prefix = "app.cache.ttl")
public class CacheTtlProperties {

    private int productDetailMinutes = 60;

    private int productDetailJitterMinutes = 5;

    public int getProductDetailMinutes() {
        return productDetailMinutes;
    }

    public void setProductDetailMinutes(int productDetailMinutes) {
        this.productDetailMinutes = productDetailMinutes;
    }

    public int getProductDetailJitterMinutes() {
        return productDetailJitterMinutes;
    }

    public void setProductDetailJitterMinutes(int productDetailJitterMinutes) {
        this.productDetailJitterMinutes = productDetailJitterMinutes;
    }

    public Duration productDetailTtl() {
        return withJitter(productDetailMinutes, productDetailJitterMinutes);
    }

    private Duration withJitter(int baseMinutes, int jitterMinutes) {
        int safeBaseMinutes = Math.max(baseMinutes, 1);
        int safeJitterMinutes = Math.max(jitterMinutes, 0);
        int randomJitter = safeJitterMinutes == 0
                ? 0
                : ThreadLocalRandom.current().nextInt(safeJitterMinutes + 1);
        return Duration.ofMinutes(safeBaseMinutes + randomJitter);
    }
}
