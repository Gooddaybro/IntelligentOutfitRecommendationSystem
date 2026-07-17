package com.recommendation.intelligentoutfitrecommendationsystem.admin.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot metrics returned to the admin dashboard.
 */
public record AdminOverviewResponse(
        long onSaleProducts,
        long skuCount,
        long lowStockCount,
        long pendingShipmentOrders,
        long afterSaleOrders,
        long orderCount,
        BigDecimal paidAmount,
        String rangeLabel,
        List<AdminTrendPoint> trend,
        List<AdminHotProduct> hotProducts
) {
}
