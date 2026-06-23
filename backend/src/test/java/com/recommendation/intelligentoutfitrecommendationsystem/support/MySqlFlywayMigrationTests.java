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

        assertThat(chatSessionCount).isPositive();
        assertThat(chatMessageCount).isPositive();
        assertThat(cartItemCount).isPositive();
        assertThat(salesOrderCount).isPositive();
        assertThat(orderItemCount).isPositive();
        assertThat(paymentCount).isPositive();
        assertThat(closedAtColumnCount).isOne();
        assertThat(closeReasonColumnCount).isOne();
    }
}
