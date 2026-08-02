package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Database projection for admin order rows before API-specific action and shipment assembly.
 */
public record AdminOrderRow(
        String orderNo,
        String username,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        long itemCount,
        LocalDateTime createdAt,
        String carrier,
        String trackingNo
) {
}
