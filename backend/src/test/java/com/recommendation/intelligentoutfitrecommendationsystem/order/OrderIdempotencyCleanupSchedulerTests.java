package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderIdempotencyCleanupScheduler;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderIdempotencyCoordinator;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderIdempotencyCleanupSchedulerTests {

    @Test
    void delegatesOneBoundedCleanupRunToCoordinator() {
        OrderIdempotencyCoordinator coordinator = mock(OrderIdempotencyCoordinator.class);
        OrderIdempotencyCleanupScheduler scheduler = new OrderIdempotencyCleanupScheduler(coordinator);

        scheduler.cleanupExpiredRecords();

        verify(coordinator).cleanupExpired();
    }
}
