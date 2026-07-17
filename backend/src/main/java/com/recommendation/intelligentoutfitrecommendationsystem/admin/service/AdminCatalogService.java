package com.recommendation.intelligentoutfitrecommendationsystem.admin.service;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminHotProduct;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminTrendPoint;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Application service backing admin dashboard and product management actions.
 */
@Service
public class AdminCatalogService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final String RANGE_LABEL = "\u6700\u8fd1 30 \u5929";

    private final AdminMapper adminMapper;

    public AdminCatalogService(AdminMapper adminMapper) {
        this.adminMapper = adminMapper;
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview() {
        return new AdminOverviewResponse(
                zero(adminMapper.countOnSaleProducts()),
                zero(adminMapper.countSkus()),
                zero(adminMapper.countLowStockSkus(LOW_STOCK_THRESHOLD)),
                zero(adminMapper.countPendingShipmentOrders()),
                zero(adminMapper.countAfterSaleOrders()),
                zero(adminMapper.countOrders()),
                zero(adminMapper.sumPaidAmount()),
                RANGE_LABEL,
                List.<AdminTrendPoint>of(),
                List.<AdminHotProduct>of()
        );
    }

    @Transactional(readOnly = true)
    public List<AdminProductResponse> listProducts() {
        return adminMapper.findProducts().stream()
                .map(this::normalizeProduct)
                .toList();
    }

    @Transactional
    public AdminProductResponse changeProductStatus(Long spuId, AdminProductStatusRequest request) {
        String dbStatus = toDbStatus(request == null ? null : request.status());
        int updated = adminMapper.updateProductStatus(spuId, dbStatus);
        if (updated == 0) {
            throw new ResourceNotFoundException("product not found");
        }
        AdminProductResponse product = adminMapper.findProductById(spuId);
        if (product == null) {
            throw new ResourceNotFoundException("product not found");
        }
        return normalizeProduct(product);
    }

    private AdminProductResponse normalizeProduct(AdminProductResponse product) {
        product.setStatus(toApiStatus(product.getStatus()));
        if (product.getStyleTags() == null) {
            product.setStyleTags(List.of());
        }
        return product;
    }

    private String toDbStatus(String status) {
        if (status == null || status.isBlank()) {
            throw new BadRequestException("status is required");
        }
        return switch (status.trim().toUpperCase(Locale.ROOT)) {
            case "ON_SALE" -> "on_sale";
            case "OFF_SHELF" -> "off_shelf";
            case "DRAFT" -> "draft";
            case "DELETED" -> "deleted";
            default -> throw new BadRequestException("unsupported product status");
        };
    }

    private String toApiStatus(String dbStatus) {
        if (dbStatus == null) {
            return null;
        }
        return dbStatus.toUpperCase(Locale.ROOT);
    }

    private long zero(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
