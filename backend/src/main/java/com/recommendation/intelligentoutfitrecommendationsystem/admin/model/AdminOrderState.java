package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

/**
 * Minimal order state read model used to guard fulfillment transitions at the admin service boundary.
 */
public record AdminOrderState(Long orderId, String orderNo, String status) {
}
