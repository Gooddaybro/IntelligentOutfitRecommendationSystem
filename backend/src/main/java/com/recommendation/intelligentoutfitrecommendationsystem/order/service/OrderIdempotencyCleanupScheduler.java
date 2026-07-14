package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 订单幂等记录的有界过期清理调度器。
 *
 * 调度器只触发单批清理，具体过期判断、批量大小和数据库事务仍由幂等协调器控制，
 * 避免后台任务绕过订单幂等边界。
 */
@Component
public class OrderIdempotencyCleanupScheduler {

    private final OrderIdempotencyCoordinator coordinator;

    public OrderIdempotencyCleanupScheduler(OrderIdempotencyCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${app.order.idempotency.cleanup-delay:PT1H}")
    public void cleanupExpiredRecords() {
        coordinator.cleanupExpired();
    }
}
