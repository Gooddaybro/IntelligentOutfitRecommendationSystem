package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Category update payload used by the admin console.
 */
public record AdminCategoryRequest(
        Long id,
        String name,
        Long parentId,
        Integer level,
        Integer sortOrder,
        Boolean enabled,
        Long productCount
) {
}