package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * User row returned to the admin user table.
 */
public record AdminUserResponse(
        Long userId,
        String username,
        String nickname,
        String email,
        String phone,
        String status,
        LocalDateTime registeredAt,
        long orderCount,
        BigDecimal paidAmount
) {
}