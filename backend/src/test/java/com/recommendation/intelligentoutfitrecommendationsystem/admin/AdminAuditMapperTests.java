package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AdminAuditMapperTests {
    @Autowired
    private AdminAuditMapper mapper;

    @Test
    @Transactional
    void insertsAndListsNewestAuditLog() {
        mapper.insertAuditLog(new AdminAuditEntry(
                "admin", "TEST_ACTION", "TEST", "42", "SUCCESS", "mapper migration"));

        assertThat(mapper.findAuditLogs(200))
                .first()
                .satisfies(row -> {
                    assertThat(row.action()).isEqualTo("TEST_ACTION");
                    assertThat(row.targetId()).isEqualTo("42");
                    assertThat(row.summary()).isEqualTo("mapper migration");
                });
    }
}
