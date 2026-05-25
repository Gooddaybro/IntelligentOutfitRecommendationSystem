package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record ProductSearchItem(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String mainImageUrl,
        String fitType,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
