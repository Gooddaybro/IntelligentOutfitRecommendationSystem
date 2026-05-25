package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record SkuSearchItem(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        BigDecimal salePrice,
        String status
) {
}
