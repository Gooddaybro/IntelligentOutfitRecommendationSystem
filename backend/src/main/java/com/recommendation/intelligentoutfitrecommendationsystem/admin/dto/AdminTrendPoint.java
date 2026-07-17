package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;

/**
 * Time series point used by admin trend charts.
 */
public record AdminTrendPoint(String label, BigDecimal amount) {
}
