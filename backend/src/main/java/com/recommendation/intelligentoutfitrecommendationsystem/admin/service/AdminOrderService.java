package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminShipOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminShipmentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminOrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderRow;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderState;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin order application service that owns fulfillment validation, shipment writes, and API response assembly.
 */
@Service
public class AdminOrderService {
    private static final String DEFAULT_OPERATOR = "admin";

    private final AdminOrderMapper adminOrderMapper;
    private final AdminAuditMapper adminAuditMapper;

    public AdminOrderService(AdminOrderMapper adminOrderMapper, AdminAuditMapper adminAuditMapper) {
        this.adminOrderMapper = adminOrderMapper;
        this.adminAuditMapper = adminAuditMapper;
    }

    /**
     * Returns order rows through a DTO boundary so controller contracts do not depend on mapper projections.
     *
     * @return admin order table rows ordered by newest order first
     */
    @Transactional(readOnly = true)
    public List<AdminOrderResponse> listOrders() {
        return adminOrderMapper.findOrders().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Ships a paid order inside one transaction and rejects stale concurrent transitions.
     *
     * @param orderNo route order number, trimmed and required before database access
     * @param request shipment payload with required carrier and tracking number
     * @return reloaded admin order row after shipment persistence
     */
    @Transactional
    public AdminOrderResponse shipOrder(String orderNo, AdminShipOrderRequest request) {
        String normalizedOrderNo = normalizeRequiredText(orderNo, "orderNo");
        String carrier = normalizeRequiredText(request == null ? null : request.carrier(), "carrier");
        String trackingNo = normalizeRequiredText(request == null ? null : request.trackingNo(), "trackingNo");
        AdminOrderState order = adminOrderMapper.findOrderStateByOrderNo(normalizedOrderNo);
        if (order == null) {
            throw new ResourceNotFoundException("order not found");
        }
        if (!"PAID".equals(order.status())) {
            throw new BadRequestException("order cannot be shipped");
        }
        if (adminOrderMapper.markOrderShipped(order.orderId()) == 0) {
            throw new BadRequestException("order cannot be shipped");
        }
        adminOrderMapper.deleteShipmentByOrderNo(normalizedOrderNo);
        adminOrderMapper.insertShipment(order.orderId(), normalizedOrderNo, carrier, trackingNo);
        insertAudit("SHIP_ORDER", "ORDER", normalizedOrderNo, "SUCCESS", carrier + " " + trackingNo);
        AdminOrderRow row = adminOrderMapper.findOrderByOrderNo(normalizedOrderNo);
        if (row == null) {
            throw new ResourceNotFoundException("order not found");
        }
        return toResponse(row);
    }

    private AdminOrderResponse toResponse(AdminOrderRow row) {
        AdminShipmentResponse shipment = row.carrier() == null ? null
                : new AdminShipmentResponse(row.carrier(), row.trackingNo());
        return new AdminOrderResponse(
                row.orderNo(),
                row.username(),
                row.status(),
                row.paymentStatus(),
                row.totalAmount(),
                row.itemCount(),
                row.createdAt(),
                availableActions(row.status()),
                null,
                shipment
        );
    }

    private List<String> availableActions(String status) {
        if ("PAID".equals(status)) {
            return List.of("SHIP");
        }
        if ("UNPAID".equals(status)) {
            return List.of("CANCEL");
        }
        if ("SHIPPED".equals(status) || "COMPLETED".equals(status)) {
            return List.of("AFTER_SALE");
        }
        return List.of();
    }

    private void insertAudit(String action, String targetType, String targetId, String result, String summary) {
        adminAuditMapper.insertAuditLog(new AdminAuditEntry(
                DEFAULT_OPERATOR,
                action,
                targetType,
                targetId,
                result,
                summary == null ? "" : summary
        ));
    }

    private String normalizeRequiredText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new BadRequestException(field + " is required");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
