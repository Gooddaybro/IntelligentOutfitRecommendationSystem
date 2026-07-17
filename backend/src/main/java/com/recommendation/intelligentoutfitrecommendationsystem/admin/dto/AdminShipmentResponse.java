package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

/**
 * Shipment fact attached to an admin order row.
 */
public record AdminShipmentResponse(String carrier, String trackingNo) {
}