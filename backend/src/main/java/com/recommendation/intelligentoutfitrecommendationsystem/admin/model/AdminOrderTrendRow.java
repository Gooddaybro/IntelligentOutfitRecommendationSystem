package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Raw sales-order row used at the mapper boundary so admin analytics can apply paid-status rules in Java.
 */
public record AdminOrderTrendRow(String status, BigDecimal totalAmount, LocalDateTime createdAt) {
}
