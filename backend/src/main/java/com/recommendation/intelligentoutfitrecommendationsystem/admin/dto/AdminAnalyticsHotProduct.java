package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;

/**
 * Hot product row in admin analytics.
 */
public record AdminAnalyticsHotProduct(Long spuId, String name, long sales, BigDecimal paidAmount) {
}