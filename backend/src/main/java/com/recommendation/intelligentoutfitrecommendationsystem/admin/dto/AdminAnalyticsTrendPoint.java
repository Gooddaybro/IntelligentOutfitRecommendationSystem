package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;

/**
 * Daily order and amount trend point.
 */
public record AdminAnalyticsTrendPoint(String label, long orderCount, BigDecimal paidAmount) {
}