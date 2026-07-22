package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

/**
 * Audit-log write model for admin operations that must be recorded inside the same business transaction.
 */
public record AdminAuditEntry(
        String operator,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary
) {
}
