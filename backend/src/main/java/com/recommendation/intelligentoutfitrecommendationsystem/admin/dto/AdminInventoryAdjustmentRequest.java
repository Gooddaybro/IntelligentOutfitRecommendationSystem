package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Request for setting a SKU to a target stock quantity.
 */
public record AdminInventoryAdjustmentRequest(Integer targetStock, String reason) {
}