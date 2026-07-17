package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business analytics returned to the admin analytics page.
 */
public record AdminAnalyticsResponse(
        String rangeLabel,
        long orderCount,
        BigDecimal paidAmount,
        AdminFunnelResponse funnel,
        List<AdminAnalyticsTrendPoint> trend,
        List<AdminAnalyticsHotProduct> hotProducts,
        List<AdminCategoryTrendPoint> categoryTrend
) {
}