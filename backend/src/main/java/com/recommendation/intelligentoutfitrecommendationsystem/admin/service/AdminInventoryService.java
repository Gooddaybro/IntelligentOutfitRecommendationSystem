package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminSkuResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminInventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminAuditEntry;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminInventoryRow;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service boundary for admin inventory management, keeping validation, transaction scope and audit semantics together.
 */
@Service
public class AdminInventoryService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String DEFAULT_OPERATOR = "admin";

    private final AdminInventoryMapper adminInventoryMapper;
    private final AdminAuditMapper adminAuditMapper;

    public AdminInventoryService(AdminInventoryMapper adminInventoryMapper, AdminAuditMapper adminAuditMapper) {
        this.adminInventoryMapper = adminInventoryMapper;
        this.adminAuditMapper = adminAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminSkuResponse> listInventory() {
        return adminInventoryMapper.findInventory().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Sets a SKU to the requested stock quantity and records the adjustment/audit rows in the same transaction.
     *
     * @param skuId SKU id from the admin route; must be positive to avoid ambiguous inventory writes
     * @param request API payload carrying the target stock and required business reason
     * @return reloaded SKU projection including the just-written latest adjustment
     */
    @Transactional
    public AdminSkuResponse adjustInventory(Long skuId, AdminInventoryAdjustmentRequest request) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        int targetStock = request == null || request.targetStock() == null ? -1 : request.targetStock();
        if (targetStock < 0) {
            throw new BadRequestException("targetStock must be non-negative");
        }
        String reason = normalizeRequiredText(request == null ? null : request.reason(), "reason");
        Integer beforeStock = adminInventoryMapper.findAvailableStockBySkuId(skuId);
        if (beforeStock == null) {
            throw new ResourceNotFoundException("sku not found");
        }
        int updated = adminInventoryMapper.updateAvailableStock(skuId, targetStock);
        if (updated == 0) {
            throw new ResourceNotFoundException("sku not found");
        }
        adminInventoryMapper.insertInventoryAdjustment(skuId, beforeStock, targetStock, reason, DEFAULT_OPERATOR);
        adminAuditMapper.insertAuditLog(new AdminAuditEntry(
                DEFAULT_OPERATOR,
                "ADJUST_STOCK",
                "SKU",
                String.valueOf(skuId),
                "SUCCESS",
                beforeStock + " -> " + targetStock
        ));
        return toResponse(requireSku(skuId));
    }

    private AdminSkuResponse toResponse(AdminInventoryRow row) {
        AdminInventoryAdjustmentResponse adjustment = row.adjustedAt() == null ? null
                : new AdminInventoryAdjustmentResponse(
                        row.beforeStock(),
                        row.afterStock(),
                        row.adjustmentReason(),
                        row.adjustmentOperator(),
                        row.adjustedAt()
                );
        return new AdminSkuResponse(
                row.skuId(),
                row.skuCode(),
                row.spuId(),
                row.productName(),
                row.color(),
                row.size(),
                row.salePrice(),
                row.availableStock(),
                LOW_STOCK_THRESHOLD,
                row.status(),
                adjustment
        );
    }

    private AdminInventoryRow requireSku(Long skuId) {
        AdminInventoryRow row = adminInventoryMapper.findSkuById(skuId);
        if (row == null) {
            throw new ResourceNotFoundException("sku not found");
        }
        return row;
    }

    private String normalizeRequiredText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(field + " is required");
        }
        return value.trim();
    }
}
