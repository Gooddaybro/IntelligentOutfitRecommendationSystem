package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class AdminUserMapperTests {
    @Autowired
    private AdminUserMapper mapper;

    @Test
    @Transactional
    void listsAndUpdatesAdminUserProjection() {
        assertThat(mapper.findUsers())
                .extracting(AdminUserResponse::userId)
                .contains(9001L);

        assertThat(mapper.updateUserStatus(9001L, "disabled")).isEqualTo(1);
        assertThat(mapper.findUserById(9001L).status()).isEqualTo("DISABLED");
    }
}