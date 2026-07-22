package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Database projection for the admin inventory table, including the latest manual adjustment kept at the SQL boundary.
 */
public record AdminInventoryRow(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        BigDecimal salePrice,
        int availableStock,
        String status,
        Integer beforeStock,
        Integer afterStock,
        String adjustmentReason,
        String adjustmentOperator,
        LocalDateTime adjustedAt
) {
}
