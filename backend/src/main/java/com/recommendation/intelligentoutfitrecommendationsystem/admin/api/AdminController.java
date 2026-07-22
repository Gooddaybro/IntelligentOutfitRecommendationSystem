package com.recommendation.intelligentoutfitrecommendationsystem.admin.api;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAnalyticsResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminInventoryAdjustmentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductInput;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminShipOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminSkuResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminAuditLogService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints used by the management console.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminCatalogService adminCatalogService;
    private final AdminAuditLogService adminAuditLogService;

    public AdminController(
            AdminCatalogService adminCatalogService,
            AdminAuditLogService adminAuditLogService) {
        this.adminCatalogService = adminCatalogService;
        this.adminAuditLogService = adminAuditLogService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> getOverview() {
        return ApiResponse.ok(adminCatalogService.getOverview());
    }

    @GetMapping("/products")
    public ApiResponse<List<AdminProductResponse>> listProducts() {
        return ApiResponse.ok(adminCatalogService.listProducts());
    }

    @PostMapping("/products")
    public ApiResponse<AdminProductResponse> createProduct(@RequestBody AdminProductInput request) {
        return ApiResponse.ok(adminCatalogService.createProduct(request));
    }

    @PutMapping("/products/{spuId}")
    public ApiResponse<AdminProductResponse> updateProduct(
            @PathVariable Long spuId,
            @RequestBody AdminProductInput request
    ) {
        return ApiResponse.ok(adminCatalogService.updateProduct(spuId, request));
    }

    @PostMapping("/products/{spuId}/status")
    public ApiResponse<AdminProductResponse> changeProductStatus(
            @PathVariable Long spuId,
            @RequestBody AdminProductStatusRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.changeProductStatus(spuId, request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<AdminCategoryResponse>> listCategories() {
        return ApiResponse.ok(adminCatalogService.listCategories());
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<AdminCategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody AdminCategoryRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.updateCategory(id, request));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<AdminSkuResponse>> listInventory() {
        return ApiResponse.ok(adminCatalogService.listInventory());
    }

    @PostMapping("/inventory/{skuId}/adjustments")
    public ApiResponse<AdminSkuResponse> adjustInventory(
            @PathVariable Long skuId,
            @RequestBody AdminInventoryAdjustmentRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.adjustInventory(skuId, request));
    }

    @GetMapping("/orders")
    public ApiResponse<List<AdminOrderResponse>> listOrders() {
        return ApiResponse.ok(adminCatalogService.listOrders());
    }

    @PostMapping("/orders/{orderNo}/ship")
    public ApiResponse<AdminOrderResponse> shipOrder(
            @PathVariable String orderNo,
            @RequestBody AdminShipOrderRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.shipOrder(orderNo, request));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        return ApiResponse.ok(adminCatalogService.listUsers());
    }

    @PostMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> changeUserStatus(
            @PathVariable Long userId,
            @RequestBody AdminUserStatusRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.changeUserStatus(userId, request));
    }

    @GetMapping("/analytics")
    public ApiResponse<AdminAnalyticsResponse> getAnalytics() {
        return ApiResponse.ok(adminCatalogService.getAnalytics());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogResponse>> listAuditLogs() {
        return ApiResponse.ok(adminAuditLogService.listAuditLogs());
    }
}
