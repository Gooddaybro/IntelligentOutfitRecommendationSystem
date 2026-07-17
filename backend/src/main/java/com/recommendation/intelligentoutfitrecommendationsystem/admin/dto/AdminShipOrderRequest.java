package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Shipment information submitted when an admin ships an order.
 */
public record AdminShipOrderRequest(String carrier, String trackingNo) {
}