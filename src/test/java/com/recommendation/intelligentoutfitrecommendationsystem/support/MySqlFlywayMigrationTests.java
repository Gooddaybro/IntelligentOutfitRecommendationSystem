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

        assertThat(chatSessionCount).isZero();
        assertThat(chatMessageCount).isZero();
    }
}
