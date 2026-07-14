package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.BuyNowRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.IdempotentOrderResult;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.support.BaseMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class OrderIdempotencyMySqlConcurrencyTests extends BaseMySqlContainerTest {

    private static final Long USER_ID = 9001L;

    private static final Long SKU_ID = 11012L;

    @Autowired
    private OrderService orderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateBuyNowCreatesOneOrderAndLocksStockOnce() throws Exception {
        String key = UUID.randomUUID().toString();
        Integer orderCountBefore = count("SELECT COUNT(*) FROM sales_order");
        Integer lockedStockBefore = count("SELECT locked_stock FROM inventory WHERE sku_id = " + SKU_ID);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<IdempotentOrderResult> first = executor.submit(() -> submit(key, ready, start));
            Future<IdempotentOrderResult> second = executor.submit(() -> submit(key, ready, start));
            ready.await();
            start.countDown();

            List<IdempotentOrderResult> results = List.of(first.get(), second.get());

            assertThat(results).extracting(result -> result.order().orderNo())
                    .containsOnly(results.getFirst().order().orderNo());
            assertThat(results).extracting(IdempotentOrderResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(count("SELECT COUNT(*) FROM sales_order")).isEqualTo(orderCountBefore + 1);
            assertThat(count("SELECT locked_stock FROM inventory WHERE sku_id = " + SKU_ID))
                    .isEqualTo(lockedStockBefore + 1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                    Integer.class,
                    USER_ID,
                    key
            )).isOne();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private IdempotentOrderResult submit(
            String key,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return orderService.buyNow(USER_ID, key, new BuyNowRequest(SKU_ID, 1));
    }

    private Integer count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}
