package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductDetail(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String description,
        String mainImageUrl,
        String fitType,
        List<String> materials,
        List<String> seasons,
        List<String> styleTags,
        Map<String, String> attributes,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
