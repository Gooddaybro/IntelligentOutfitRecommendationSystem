package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record RecommendationCandidate(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String mainImageUrl,
        String fitType,
        String materials,
        String seasons,
        String styleTags,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer totalAvailableStock
) {
}
