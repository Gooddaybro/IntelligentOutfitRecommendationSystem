package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;

/**
 * SKU and stock row returned to the admin inventory table.
 */
public record AdminSkuResponse(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        BigDecimal salePrice,
        int availableStock,
        int lowStockThreshold,
        String status,
        AdminInventoryAdjustmentResponse lastAdjustment
) {
}