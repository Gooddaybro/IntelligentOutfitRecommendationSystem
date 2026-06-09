package com.recommendation.intelligentoutfitrecommendationsystem.auth.api;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.CurrentUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.service.AuthService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户账户信息接口。
 *
 * 和 /api/me/profile 不同，这里返回的是账号与角色信息，用于前端初始化登录态。
 */
@RestController
@RequestMapping("/api/users")
public class CurrentUserController {

    private final AuthService authService;

    public CurrentUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(authService.getCurrentUser(currentUser.userId()));
    }
}
