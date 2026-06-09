package com.recommendation.intelligentoutfitrecommendationsystem.auth.api;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.AuthClientContext;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.AuthTokenResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.LoginRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.LogoutRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.RefreshTokenRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.RegisterRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.RegisterResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.service.AuthService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户认证入口。
 *
 * 这里不接收 Bearer Token，而是负责注册、登录、刷新和登出；
 * 成功登录后客户端再使用返回的 accessToken 访问 /api/me/** 等受保护接口。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(authService.login(request, clientContext(servletRequest)));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthTokenResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.ok(authService.refresh(request.refreshToken(), clientContext(servletRequest)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.ok(null);
    }

    private AuthClientContext clientContext(HttpServletRequest request) {
        // 设备、UA 和 IP 会随 Refresh Token 存档，后续可用于多端登录和安全审计。
        return new AuthClientContext(
                request.getHeader("X-Device-Id"),
                request.getHeader("User-Agent"),
                clientIp(request)
        );
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // 兼容反向代理部署，取第一个 IP 作为真实客户端来源。
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
