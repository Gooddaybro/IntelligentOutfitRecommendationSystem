package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Product payload submitted by the admin product editor.
 */
public record AdminProductInput(
        Long spuId,
        String spuCode,
        String name,
        Long categoryId,
        String categoryName,
        String mainImageUrl,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        String status,
        String description,
        List<String> styleTags
) {
}