package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.time.LocalDateTime;

/**
 * Immutable audit log row exposed to the admin console.
 */
public record AdminAuditLogResponse(
        Long id,
        String operator,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary,
        LocalDateTime createdAt
) {
}