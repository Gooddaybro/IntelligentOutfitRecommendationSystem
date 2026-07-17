package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Hot product item shown on the admin dashboard.
 */
public record AdminHotProduct(Long spuId, String name, long sales) {
}
