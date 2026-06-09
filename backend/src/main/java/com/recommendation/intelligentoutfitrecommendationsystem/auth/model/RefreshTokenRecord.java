package com.recommendation.intelligentoutfitrecommendationsystem.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Refresh Token 的数据库存根。
 *
 * tokenHash 保存明文 token 的 SHA-256 摘要，revokedAt 用于退出登录和滚动刷新后的失效控制。
 */
@Data
public class RefreshTokenRecord {

    private Long id;

    private Long userId;

    private String tokenHash;

    private String deviceId;

    private String userAgent;

    private String ipAddress;

    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;
}
