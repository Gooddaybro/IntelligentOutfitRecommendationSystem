package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Admin analytics funnel metrics.
 */
public record AdminFunnelResponse(
        long exposed,
        long clicked,
        long cartAdded,
        long purchased,
        String definition
) {
}