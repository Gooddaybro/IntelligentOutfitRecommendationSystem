package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Category row returned to the admin category table.
 */
public record AdminCategoryResponse(
        Long id,
        String name,
        Long parentId,
        Integer level,
        Integer sortOrder,
        boolean enabled,
        long productCount
) {
}