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
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminAnalyticsService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminAuditLogService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminInventoryService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminProductService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminOrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminUserService;
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
    private final AdminAnalyticsService adminAnalyticsService;
    private final AdminAuditLogService adminAuditLogService;
    private final AdminProductService adminProductService;
    private final AdminInventoryService adminInventoryService;
    private final AdminOrderService adminOrderService;
    private final AdminUserService adminUserService;

    public AdminController(
            AdminAnalyticsService adminAnalyticsService,
            AdminAuditLogService adminAuditLogService,
            AdminProductService adminProductService,
            AdminInventoryService adminInventoryService,
            AdminOrderService adminOrderService,
            AdminUserService adminUserService) {
        this.adminAnalyticsService = adminAnalyticsService;
        this.adminAuditLogService = adminAuditLogService;
        this.adminProductService = adminProductService;
        this.adminInventoryService = adminInventoryService;
        this.adminOrderService = adminOrderService;
        this.adminUserService = adminUserService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> getOverview() {
        return ApiResponse.ok(adminAnalyticsService.getOverview());
    }

    @GetMapping("/products")
    public ApiResponse<List<AdminProductResponse>> listProducts() {
        return ApiResponse.ok(adminProductService.listProducts());
    }

    @PostMapping("/products")
    public ApiResponse<AdminProductResponse> createProduct(@RequestBody AdminProductInput request) {
        return ApiResponse.ok(adminProductService.createProduct(request));
    }

    @PutMapping("/products/{spuId}")
    public ApiResponse<AdminProductResponse> updateProduct(
            @PathVariable Long spuId,
            @RequestBody AdminProductInput request
    ) {
        return ApiResponse.ok(adminProductService.updateProduct(spuId, request));
    }

    @PostMapping("/products/{spuId}/status")
    public ApiResponse<AdminProductResponse> changeProductStatus(
            @PathVariable Long spuId,
            @RequestBody AdminProductStatusRequest request
    ) {
        return ApiResponse.ok(adminProductService.changeProductStatus(spuId, request));
    }

    @GetMapping("/categories")
    public ApiResponse<List<AdminCategoryResponse>> listCategories() {
        return ApiResponse.ok(adminProductService.listCategories());
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<AdminCategoryResponse> updateCategory(
            @PathVariable Long id,
            @RequestBody AdminCategoryRequest request
    ) {
        return ApiResponse.ok(adminProductService.updateCategory(id, request));
    }

    @GetMapping("/inventory")
    public ApiResponse<List<AdminSkuResponse>> listInventory() {
        return ApiResponse.ok(adminInventoryService.listInventory());
    }

    @PostMapping("/inventory/{skuId}/adjustments")
    public ApiResponse<AdminSkuResponse> adjustInventory(
            @PathVariable Long skuId,
            @RequestBody AdminInventoryAdjustmentRequest request
    ) {
        return ApiResponse.ok(adminInventoryService.adjustInventory(skuId, request));
    }

    @GetMapping("/orders")
    public ApiResponse<List<AdminOrderResponse>> listOrders() {
        return ApiResponse.ok(adminOrderService.listOrders());
    }

    @PostMapping("/orders/{orderNo}/ship")
    public ApiResponse<AdminOrderResponse> shipOrder(
            @PathVariable String orderNo,
            @RequestBody AdminShipOrderRequest request
    ) {
        return ApiResponse.ok(adminOrderService.shipOrder(orderNo, request));
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> listUsers() {
        return ApiResponse.ok(adminUserService.listUsers());
    }

    @PostMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> changeUserStatus(
            @PathVariable Long userId,
            @RequestBody AdminUserStatusRequest request
    ) {
        return ApiResponse.ok(adminUserService.changeUserStatus(userId, request));
    }

    @GetMapping("/analytics")
    public ApiResponse<AdminAnalyticsResponse> getAnalytics() {
        return ApiResponse.ok(adminAnalyticsService.getAnalytics());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<List<AdminAuditLogResponse>> listAuditLogs() {
        return ApiResponse.ok(adminAuditLogService.listAuditLogs());
    }
}
