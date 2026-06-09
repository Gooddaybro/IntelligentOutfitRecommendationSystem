package com.recommendation.intelligentoutfitrecommendationsystem.auth;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.LoginLog;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.RefreshTokenRecord;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class UserAuthMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(3000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Test
    void insertsUserRoleRefreshTokenAndLoginLog() {
        String username = "mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");

        userAuthMapper.insertUserAccount(userAccount);
        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);

        RefreshTokenRecord refreshToken = new RefreshTokenRecord();
        refreshToken.setUserId(userAccount.getId());
        refreshToken.setTokenHash("token_hash_" + username);
        refreshToken.setExpiresAt(LocalDateTime.now().plusDays(1));
        refreshToken.setLastUsedAt(LocalDateTime.now());
        userAuthMapper.insertRefreshToken(refreshToken);

        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userAccount.getId());
        loginLog.setUsername(username);
        loginLog.setSuccess(true);
        userAuthMapper.insertLoginLog(loginLog);

        assertThat(userAuthMapper.existsByUsername(username)).isEqualTo(1);
        assertThat(userAuthMapper.findByUsername(username).getId()).isEqualTo(userAccount.getId());
        assertThat(userAuthMapper.findRoleCodesByUserId(userAccount.getId())).containsExactly("USER");
        assertThat(userAuthMapper.findRefreshTokenByHash(refreshToken.getTokenHash()).getUserId())
                .isEqualTo(userAccount.getId());
    }
}
