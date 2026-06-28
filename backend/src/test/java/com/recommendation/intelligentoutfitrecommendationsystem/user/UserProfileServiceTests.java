package com.recommendation.intelligentoutfitrecommendationsystem.user;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.mapper.UserProfileMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserProfile;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTests {

    @Mock
    private UserProfileMapper userProfileMapper;

    @Mock
    private RedisCacheService redisCacheService;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        CacheTtlProperties cacheTtlProperties = new CacheTtlProperties();
        cacheTtlProperties.setUserProfileJitterMinutes(0);
        service = new UserProfileService(userProfileMapper, redisCacheService, cacheTtlProperties);
    }

    @Test
    void getProfileReturnsCachedProfileWithoutQueryingMapper() {
        UserProfileResponse cachedProfile = new UserProfileResponse(
                1001L,
                "Cached Alex",
                "https://example.com/avatar.png",
                "male",
                LocalDate.of(1998, 5, 20)
        );
        when(redisCacheService.getValue(CacheKeyConstants.userProfile(1001L), UserProfileResponse.class))
                .thenReturn(Optional.of(cachedProfile));

        UserProfileResponse response = service.getProfile(1001L);

        assertThat(response.nickname()).isEqualTo("Cached Alex");
        verify(userProfileMapper, never()).findProfileByUserId(1001L);
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    @Test
    void getProfileWritesDatabaseResultToCacheWhenCacheMisses() {
        when(redisCacheService.getValue(CacheKeyConstants.userProfile(1001L), UserProfileResponse.class))
                .thenReturn(Optional.empty());
        when(userProfileMapper.findProfileByUserId(1001L)).thenReturn(profile());

        UserProfileResponse response = service.getProfile(1001L);

        assertThat(response.nickname()).isEqualTo("Mapper Alex");
        verify(redisCacheService).setValue(
                eq(CacheKeyConstants.userProfile(1001L)),
                eq(response),
                any(Duration.class)
        );
    }

    @Test
    void updateProfileDeletesCacheAfterWritingDatabase() {
        when(userProfileMapper.findProfileByUserId(1001L)).thenReturn(profile());
        UserProfileRequest request = new UserProfileRequest(
                "Updated Alex",
                "https://example.com/new-avatar.png",
                "male",
                LocalDate.of(1999, 1, 1)
        );

        UserProfileResponse response = service.updateProfile(1001L, request);

        assertThat(response.nickname()).isEqualTo("Updated Alex");
        verify(userProfileMapper).updateProfile(any(UserProfile.class));
        verify(redisCacheService).delete(CacheKeyConstants.userProfile(1001L));
    }

    private UserProfile profile() {
        UserProfile profile = new UserProfile();
        profile.setUserId(1001L);
        profile.setNickname("Mapper Alex");
        profile.setAvatarUrl("https://example.com/avatar.png");
        profile.setGender("male");
        profile.setBirthday(LocalDate.of(1998, 5, 20));
        return profile;
    }
}
