package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order row returned to the admin order table.
 */
public record AdminOrderResponse(
        String orderNo,
        String username,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        long itemCount,
        LocalDateTime createdAt,
        List<String> availableActions,
        String addressSummary,
        AdminShipmentResponse shipment
) {
}