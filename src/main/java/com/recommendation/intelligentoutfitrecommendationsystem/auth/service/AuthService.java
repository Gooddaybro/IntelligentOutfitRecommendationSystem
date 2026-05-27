package com.recommendation.intelligentoutfitrecommendationsystem.auth.service;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.AuthClientContext;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.AuthTokenResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.CurrentUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.LoginRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.RegisterRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.dto.RegisterResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.LoginLog;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.RefreshTokenRecord;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.security.JwtProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * 用户认证核心服务。
 *
 * 这里集中处理账号唯一性、密码 Hash、双 Token 签发、Refresh Token 撤销和登录审计，
 * 保证 Controller 只表达 HTTP 契约，Mapper 只表达 SQL。
 */
@Service
public class AuthService {

    private static final String DEFAULT_USER_ROLE = "USER";

    private final UserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserAuthMapper userAuthMapper,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            JwtProperties jwtProperties
    ) {
        this.userAuthMapper = userAuthMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        assertUniqueAccount(request);

        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(request.username().trim());
        userAccount.setPhone(normalize(request.phone()));
        userAccount.setEmail(normalize(request.email()));
        userAccount.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        // 注册即绑定 USER 角色，保证用户第一次登录后 JWT 中已有稳定的权限声明。
        Long roleId = userAuthMapper.findRoleIdByCode(DEFAULT_USER_ROLE);
        if (roleId == null) {
            throw new IllegalStateException("default USER role is missing");
        }
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);

        return new RegisterResponse(userAccount.getId(), userAccount.getUsername(), userAccount.getStatus());
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request, AuthClientContext clientContext) {
        UserAccount user = userAuthMapper.findByUsername(request.username());
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            writeLoginLog(null, request.username(), false, "bad credentials", clientContext);
            throw new BadRequestException("username or password is incorrect");
        }
        if (!"active".equals(user.getStatus())) {
            writeLoginLog(user.getId(), user.getUsername(), false, "account disabled", clientContext);
            throw new BadRequestException("account is not active");
        }

        // 登录成功和失败都写审计日志，后续可接入风控、告警或登录历史查询。
        writeLoginLog(user.getId(), user.getUsername(), true, null, clientContext);
        return issueTokens(user, clientContext);
    }

    @Transactional
    public AuthTokenResponse refresh(String rawRefreshToken, AuthClientContext clientContext) {
        RefreshTokenRecord refreshToken = loadValidRefreshToken(rawRefreshToken);
        LocalDateTime now = LocalDateTime.now();
        userAuthMapper.markRefreshTokenUsed(refreshToken.getId(), now);
        // Refresh Token 采用滚动刷新：旧 token 验证成功后立即撤销，只返回新的一套 token。
        userAuthMapper.revokeRefreshToken(refreshToken.getId(), now);

        UserAccount user = userAuthMapper.findById(refreshToken.getUserId());
        if (user == null || !"active".equals(user.getStatus())) {
            throw new BadRequestException("refresh token is invalid");
        }
        return issueTokens(user, clientContext);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshTokenRecord refreshToken = userAuthMapper.findRefreshTokenByHash(hashToken(rawRefreshToken));
        if (refreshToken != null) {
            userAuthMapper.revokeRefreshToken(refreshToken.getId(), LocalDateTime.now());
        }
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse getCurrentUser(Long userId) {
        UserAccount user = userAuthMapper.findById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("user not found: " + userId);
        }
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getStatus(), authorityRoles(user.getId()));
    }

    private void assertUniqueAccount(RegisterRequest request) {
        if (userAuthMapper.existsByUsername(request.username().trim()) > 0) {
            throw new BadRequestException("username already exists");
        }
        if (StringUtils.hasText(request.phone()) && userAuthMapper.existsByPhone(request.phone().trim()) > 0) {
            throw new BadRequestException("phone already exists");
        }
        if (StringUtils.hasText(request.email()) && userAuthMapper.existsByEmail(request.email().trim()) > 0) {
            throw new BadRequestException("email already exists");
        }
    }

    private AuthTokenResponse issueTokens(UserAccount user, AuthClientContext clientContext) {
        List<String> roles = authorityRoles(user.getId());
        String accessToken = jwtService.createAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = createRefreshToken(user.getId(), clientContext);
        return new AuthTokenResponse(accessToken, refreshToken, "Bearer", jwtService.accessTokenSeconds());
    }

    private List<String> authorityRoles(Long userId) {
        return userAuthMapper.findRoleCodesByUserId(userId).stream()
                .map(role -> "ROLE_" + role)
                .toList();
    }

    private String createRefreshToken(Long userId, AuthClientContext clientContext) {
        String rawToken = generateRefreshToken();
        RefreshTokenRecord record = new RefreshTokenRecord();
        record.setUserId(userId);
        // 数据库只存 SHA-256 摘要，避免 refresh token 明文随数据库泄露而直接可用。
        record.setTokenHash(hashToken(rawToken));
        record.setDeviceId(clientContext.deviceId());
        record.setUserAgent(clientContext.userAgent());
        record.setIpAddress(clientContext.ipAddress());
        record.setExpiresAt(LocalDateTime.now().plusDays(jwtProperties.getRefreshTokenDays()));
        record.setLastUsedAt(LocalDateTime.now());
        userAuthMapper.insertRefreshToken(record);
        return rawToken;
    }

    private RefreshTokenRecord loadValidRefreshToken(String rawRefreshToken) {
        RefreshTokenRecord refreshToken = userAuthMapper.findRefreshTokenByHash(hashToken(rawRefreshToken));
        if (refreshToken == null || refreshToken.getRevokedAt() != null
                || !refreshToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("refresh token is invalid");
        }
        return refreshToken;
    }

    private void writeLoginLog(
            Long userId,
            String username,
            boolean success,
            String failReason,
            AuthClientContext clientContext
    ) {
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setSuccess(success);
        loginLog.setFailReason(failReason);
        loginLog.setIpAddress(clientContext.ipAddress());
        loginLog.setUserAgent(clientContext.userAgent());
        userAuthMapper.insertLoginLog(loginLog);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        // URL-safe 编码便于客户端在 JSON、Header 或移动端安全保存，不需要额外转义。
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
