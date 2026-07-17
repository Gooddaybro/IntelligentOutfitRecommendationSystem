package com.recommendation.intelligentoutfitrecommendationsystem.admin.api;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminOverviewResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminProductStatusRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.service.AdminCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public AdminController(AdminCatalogService adminCatalogService) {
        this.adminCatalogService = adminCatalogService;
    }

    @GetMapping("/overview")
    public ApiResponse<AdminOverviewResponse> getOverview() {
        return ApiResponse.ok(adminCatalogService.getOverview());
    }

    @GetMapping("/products")
    public ApiResponse<List<AdminProductResponse>> listProducts() {
        return ApiResponse.ok(adminCatalogService.listProducts());
    }

    @PostMapping("/products/{spuId}/status")
    public ApiResponse<AdminProductResponse> changeProductStatus(
            @PathVariable Long spuId,
            @RequestBody AdminProductStatusRequest request
    ) {
        return ApiResponse.ok(adminCatalogService.changeProductStatus(spuId, request));
    }
}
