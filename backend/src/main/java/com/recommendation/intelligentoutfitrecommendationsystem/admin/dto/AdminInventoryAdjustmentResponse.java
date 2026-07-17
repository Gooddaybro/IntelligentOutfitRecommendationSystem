package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.time.LocalDateTime;

/**
 * Last recorded manual inventory adjustment for a SKU.
 */
public record AdminInventoryAdjustmentResponse(
        int beforeStock,
        int afterStock,
        String reason,
        String operator,
        LocalDateTime adjustedAt
) {
}