package com.recommendation.intelligentoutfitrecommendationsystem.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.BodyMeasurementsPatchRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.mapper.UserProfileMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserBodyData;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserPreferences;
import com.recommendation.intelligentoutfitrecommendationsystem.user.model.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前用户画像服务。
 *
 * 画像数据会作为后续 Java 调 Python AI 推荐服务的结构化上下文，
 * 因此这里保持字段拆分清晰：基础资料、身体数据、穿衣偏好分别持久化。
 */
@Service
public class UserProfileService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserProfileMapper userProfileMapper;
    private final RedisCacheService redisCacheService;
    private final CacheTtlProperties cacheTtlProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserProfileService(
            UserProfileMapper userProfileMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties
    ) {
        this.userProfileMapper = userProfileMapper;
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        String cacheKey = CacheKeyConstants.userProfile(userId);
        var cachedProfile = redisCacheService.getValue(cacheKey, UserProfileResponse.class);
        if (cachedProfile.isPresent()) {
            return cachedProfile.get();
        }

        UserProfile profile = userProfileMapper.findProfileByUserId(userId);
        UserProfileResponse response;
        if (profile == null) {
            // 首次进入资料页时返回空画像对象，前端可直接渲染编辑表单。
            response = new UserProfileResponse(userId, null, null, null, null);
        } else {
            response = toProfileResponse(userId, profile);
        }
        redisCacheService.setValue(cacheKey, response, cacheTtlProperties.userProfileTtl());
        return response;
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileRequest request) {
        UserProfile profile = new UserProfile();
        profile.setUserId(userId);
        profile.setNickname(request.nickname());
        profile.setAvatarUrl(request.avatarUrl());
        profile.setGender(request.gender());
        profile.setBirthday(request.birthday());

        if (userProfileMapper.findProfileByUserId(userId) == null) {
            userProfileMapper.insertProfile(profile);
        } else {
            userProfileMapper.updateProfile(profile);
        }
        // 先写 MySQL，再删除缓存，保证用户画像事实源仍然是数据库。
        redisCacheService.delete(CacheKeyConstants.userProfile(userId));
        return toProfileResponse(userId, profile);
    }

    @Transactional(readOnly = true)
    public UserBodyDataResponse getBodyData(Long userId) {
        UserBodyData bodyData = userProfileMapper.findBodyDataByUserId(userId);
        if (bodyData == null) {
            return new UserBodyDataResponse(userId, null, null, null, null, null, null, null, null);
        }
        return toBodyDataResponse(userId, bodyData);
    }

    @Transactional
    public UserBodyDataResponse updateBodyData(Long userId, UserBodyDataRequest request) {
        UserBodyData bodyData = new UserBodyData();
        bodyData.setUserId(userId);
        bodyData.setHeightCm(request.heightCm());
        bodyData.setWeightKg(request.weightKg());
        bodyData.setGender(request.gender());
        bodyData.setShoulderWidthCm(request.shoulderWidthCm());
        bodyData.setBustCm(request.bustCm());
        bodyData.setWaistCm(request.waistCm());
        bodyData.setHipCm(request.hipCm());
        bodyData.setPreferredFit(request.preferredFit());

        if (userProfileMapper.findBodyDataByUserId(userId) == null) {
            userProfileMapper.insertBodyData(bodyData);
        } else {
            userProfileMapper.updateBodyData(bodyData);
        }
        return toBodyDataResponse(userId, bodyData);
    }

    /** Updates only explicitly supplied measurements and preserves all other body profile fields. */
    @Transactional
    public UserBodyDataResponse updateBodyMeasurements(Long userId, BodyMeasurementsPatchRequest request) {
        if (request == null || request.heightCm() == null && request.weightKg() == null) {
            throw new BadRequestException("heightCm or weightKg is required");
        }
        UserBodyData existing = userProfileMapper.findBodyDataByUserId(userId);
        if (existing == null) {
            UserBodyData bodyData = new UserBodyData();
            bodyData.setUserId(userId);
            bodyData.setHeightCm(request.heightCm());
            bodyData.setWeightKg(request.weightKg());
            userProfileMapper.insertBodyData(bodyData);
        } else {
            userProfileMapper.updateBodyMeasurements(userId, request.heightCm(), request.weightKg());
        }
        UserBodyData updated = userProfileMapper.findBodyDataByUserId(userId);
        if (updated == null) {
            throw new IllegalStateException("body measurements were not persisted");
        }
        return toBodyDataResponse(userId, updated);
    }

    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(Long userId) {
        UserPreferences preferences = userProfileMapper.findPreferencesByUserId(userId);
        if (preferences == null) {
            // 偏好列表默认返回空数组，避免调用方区分 null 和 empty 两套语义。
            return new UserPreferencesResponse(userId, List.of(), List.of(), List.of(), List.of(), null, null);
        }
        return toPreferencesResponse(userId, preferences);
    }

    @Transactional
    public UserPreferencesResponse updatePreferences(Long userId, UserPreferencesRequest request) {
        validateBudgetRange(request.budgetMin(), request.budgetMax());

        UserPreferences preferences = new UserPreferences();
        preferences.setUserId(userId);
        // 多值偏好先以 JSON 文本落库，保留结构化语义，同时避免过早拆出多张偏好关系表。
        preferences.setPreferredStyles(writeStringList(request.preferredStyles()));
        preferences.setPreferredColors(writeStringList(request.preferredColors()));
        preferences.setDislikedColors(writeStringList(request.dislikedColors()));
        preferences.setPreferredCategories(writeStringList(request.preferredCategories()));
        preferences.setBudgetMin(request.budgetMin());
        preferences.setBudgetMax(request.budgetMax());

        if (userProfileMapper.findPreferencesByUserId(userId) == null) {
            userProfileMapper.insertPreferences(preferences);
        } else {
            userProfileMapper.updatePreferences(preferences);
        }
        return toPreferencesResponse(userId, preferences);
    }

    private UserProfileResponse toProfileResponse(Long userId, UserProfile profile) {
        return new UserProfileResponse(
                userId,
                profile.getNickname(),
                profile.getAvatarUrl(),
                profile.getGender(),
                profile.getBirthday()
        );
    }

    private UserBodyDataResponse toBodyDataResponse(Long userId, UserBodyData bodyData) {
        return new UserBodyDataResponse(
                userId,
                bodyData.getHeightCm(),
                bodyData.getWeightKg(),
                bodyData.getGender(),
                bodyData.getShoulderWidthCm(),
                bodyData.getBustCm(),
                bodyData.getWaistCm(),
                bodyData.getHipCm(),
                bodyData.getPreferredFit()
        );
    }

    private UserPreferencesResponse toPreferencesResponse(Long userId, UserPreferences preferences) {
        return new UserPreferencesResponse(
                userId,
                readStringList(preferences.getPreferredStyles()),
                readStringList(preferences.getPreferredColors()),
                readStringList(preferences.getDislikedColors()),
                readStringList(preferences.getPreferredCategories()),
                preferences.getBudgetMin(),
                preferences.getBudgetMax()
        );
    }

    private void validateBudgetRange(BigDecimal budgetMin, BigDecimal budgetMax) {
        if (budgetMin != null && budgetMax != null && budgetMin.compareTo(budgetMax) > 0) {
            throw new BadRequestException("budgetMin must be less than or equal to budgetMax");
        }
    }

    private String writeStringList(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("preference list is invalid");
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("stored preference list is invalid");
        }
    }
}
