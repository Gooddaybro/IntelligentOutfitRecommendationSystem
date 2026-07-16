package com.recommendation.intelligentoutfitrecommendationsystem.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class MySqlFlywayMigrationTests extends BaseMySqlContainerTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void appliesFlywayMigrationsToMySql() {
        Integer chatSessionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_session", Integer.class);
        Integer chatMessageCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_message", Integer.class);
        Integer cartItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart_item", Integer.class);
        Integer salesOrderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sales_order", Integer.class);
        Integer orderItemCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_item", Integer.class);
        Integer paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
        Integer orderIdempotencyTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_idempotency'
                """, Integer.class);
        Integer idempotencyUniqueIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_idempotency'
                  AND INDEX_NAME = 'uk_order_idempotency'
                """, Integer.class);
        Integer closedAtColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'sales_order'
                  AND COLUMN_NAME = 'closed_at'
                """, Integer.class);
        Integer closeReasonColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = 'sales_order'
                  AND COLUMN_NAME = 'close_reason'
                """, Integer.class);
        Integer aiReliabilityTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('ai_task', 'outbox_event', 'consumer_inbox', 'ai_task_redrive_audit')
                """, Integer.class);
        Integer demandStateTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('chat_demand_state', 'chat_demand_transition')
                """, Integer.class);

        assertThat(chatSessionCount).isPositive();
        assertThat(chatMessageCount).isPositive();
        assertThat(cartItemCount).isPositive();
        assertThat(salesOrderCount).isPositive();
        assertThat(orderItemCount).isPositive();
        assertThat(paymentCount).isPositive();
        assertThat(orderIdempotencyTableCount).isOne();
        assertThat(idempotencyUniqueIndexCount).isPositive();
        assertThat(closedAtColumnCount).isOne();
        assertThat(closeReasonColumnCount).isOne();
        assertThat(aiReliabilityTableCount).isEqualTo(4);
        assertThat(demandStateTableCount).isEqualTo(2);
    }
}
