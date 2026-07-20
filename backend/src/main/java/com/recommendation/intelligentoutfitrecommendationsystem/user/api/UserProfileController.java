package com.recommendation.intelligentoutfitrecommendationsystem.user.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.BodyMeasurementsPatchRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户画像接口。
 *
 * 所有读写都绑定 JWT 中的当前 userId，不开放 userId 路径参数，
 * 这样用户只能维护自己的基础资料、身体数据和穿衣偏好。
 */
@RestController
@RequestMapping("/api/me")
public class UserProfileController {

    private final UserProfileService userProfileService;

    public UserProfileController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.getProfile(currentUser.userId()));
    }

    @PutMapping("/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UserProfileRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.updateProfile(currentUser.userId(), request));
    }

    @GetMapping("/body-data")
    public ApiResponse<UserBodyDataResponse> getBodyData(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.getBodyData(currentUser.userId()));
    }

    @PutMapping("/body-data")
    public ApiResponse<UserBodyDataResponse> updateBodyData(
            Authentication authentication,
            @Valid @RequestBody UserBodyDataRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.updateBodyData(currentUser.userId(), request));
    }

    @PatchMapping("/body-data/measurements")
    public ApiResponse<UserBodyDataResponse> updateBodyMeasurements(
            Authentication authentication,
            @Valid @RequestBody BodyMeasurementsPatchRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.updateBodyMeasurements(currentUser.userId(), request));
    }

    @GetMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> getPreferences(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.getPreferences(currentUser.userId()));
    }

    @PutMapping("/preferences")
    public ApiResponse<UserPreferencesResponse> updatePreferences(
            Authentication authentication,
            @Valid @RequestBody UserPreferencesRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(userProfileService.updatePreferences(currentUser.userId(), request));
    }
}
