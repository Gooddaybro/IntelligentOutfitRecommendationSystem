package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 订单创建幂等记录的保留与清理配置。
 *
 * 有效期决定客户端购买意图可以安全重放多久，批量配置限制后台清理的单次事务规模。
 */
@ConfigurationProperties(prefix = "app.order.idempotency")
public class OrderIdempotencyProperties {

    private Duration retention = Duration.ofHours(24);

    private Duration cleanupDelay = Duration.ofHours(1);

    private int cleanupBatchSize = 500;

    public Duration getRetention() {
        return retention;
    }

    public void setRetention(Duration retention) {
        this.retention = retention;
    }

    public Duration getCleanupDelay() {
        return cleanupDelay;
    }

    public void setCleanupDelay(Duration cleanupDelay) {
        this.cleanupDelay = cleanupDelay;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }
}
