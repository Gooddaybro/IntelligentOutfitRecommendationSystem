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
        Integer addressTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME IN ('user_address', 'order_address_snapshot')
                """, Integer.class);
        Integer addressIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_address'
                  AND INDEX_NAME IN ('idx_user_address_default', 'idx_user_address_updated')
                """, Integer.class);
        String defaultIndexColumns = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_address'
                  AND INDEX_NAME = 'idx_user_address_default'
                """, String.class);
        String updatedIndexColumns = jdbcTemplate.queryForObject("""
                SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ',')
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_address'
                  AND INDEX_NAME = 'idx_user_address_updated'
                """, String.class);
        Integer addressColumnDefinitionCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'user_address'
                  AND IS_NULLABLE = 'NO'
                  AND (
                      (COLUMN_NAME = 'user_id' AND DATA_TYPE = 'bigint')
                      OR (COLUMN_NAME = 'recipient_name' AND CHARACTER_MAXIMUM_LENGTH = 64)
                      OR (COLUMN_NAME = 'phone' AND CHARACTER_MAXIMUM_LENGTH = 32)
                      OR (COLUMN_NAME IN ('province', 'city', 'district') AND CHARACTER_MAXIMUM_LENGTH = 64)
                      OR (COLUMN_NAME = 'detail' AND CHARACTER_MAXIMUM_LENGTH = 255)
                      OR (COLUMN_NAME = 'is_default' AND DATA_TYPE = 'tinyint' AND COLUMN_DEFAULT = '0')
                      OR (COLUMN_NAME IN ('created_at', 'updated_at') AND DATA_TYPE = 'datetime')
                  )
                """, Integer.class);
        Integer snapshotColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_address_snapshot'
                  AND COLUMN_NAME IN (
                      'order_id', 'source_address_id', 'recipient_name', 'phone',
                      'province', 'city', 'district', 'detail'
                  )
                """, Integer.class);
        Integer snapshotUniqueOrderCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_address_snapshot'
                  AND INDEX_NAME = 'uk_order_address_snapshot_order'
                  AND COLUMN_NAME = 'order_id'
                  AND NON_UNIQUE = 0
                """, Integer.class);
        Integer snapshotOrderForeignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_address_snapshot'
                  AND COLUMN_NAME = 'order_id'
                  AND REFERENCED_TABLE_NAME = 'sales_order'
                """, Integer.class);
        Integer snapshotSourceForeignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'order_address_snapshot'
                  AND COLUMN_NAME = 'source_address_id'
                  AND REFERENCED_TABLE_NAME IS NOT NULL
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
        assertThat(addressTableCount).isEqualTo(2);
        assertThat(addressIndexCount).isEqualTo(2);
        assertThat(defaultIndexColumns).isEqualTo("user_id,is_default");
        assertThat(updatedIndexColumns).isEqualTo("user_id,updated_at");
        assertThat(addressColumnDefinitionCount).isEqualTo(10);
        assertThat(snapshotColumnCount).isEqualTo(8);
        assertThat(snapshotUniqueOrderCount).isOne();
        assertThat(snapshotOrderForeignKeyCount).isOne();
        assertThat(snapshotSourceForeignKeyCount).isZero();
    }
}
