package com.recommendation.intelligentoutfitrecommendationsystem.user;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.user.mapper.UserProfileMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserBodyData;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserPreferences;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class UserProfileMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(4000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Test
    void insertsAndReadsProfileBodyDataAndPreferences() {
        Long userId = createUser();

        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setNickname("Mapper Alex");
        profile.setGender("male");
        profile.setBirthday(LocalDate.of(1998, 5, 20));
        userProfileMapper.insertProfile(profile);

        UserBodyData bodyData = new UserBodyData();
        bodyData.setUserId(userId);
        bodyData.setHeightCm(new BigDecimal("178.50"));
        bodyData.setPreferredFit("regular");
        userProfileMapper.insertBodyData(bodyData);

        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(userId);
        preferences.setPreferredStyles("[\"commute\"]");
        preferences.setPreferredColors("[\"black\"]");
        preferences.setBudgetMin(new BigDecimal("100.00"));
        preferences.setBudgetMax(new BigDecimal("500.00"));
        userProfileMapper.insertPreferences(preferences);

        assertThat(userProfileMapper.findProfileByUserId(userId).getNickname()).isEqualTo("Mapper Alex");
        assertThat(userProfileMapper.findBodyDataByUserId(userId).getPreferredFit()).isEqualTo("regular");
        assertThat(userProfileMapper.findPreferencesByUserId(userId).getPreferredStyles()).contains("commute");
    }

    private Long createUser() {
        String username = "profile_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
