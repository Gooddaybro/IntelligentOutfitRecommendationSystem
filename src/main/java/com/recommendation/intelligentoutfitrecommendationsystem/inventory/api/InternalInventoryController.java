package com.recommendation.intelligentoutfitrecommendationsystem.inventory.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 库存模块
 */
@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventoryQueryService inventoryQueryService;

    public InternalInventoryController(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @GetMapping("/inventory")
    public ApiResponse<InventoryView> getInventory(@RequestParam Long skuId) {
        return ApiResponse.ok(inventoryQueryService.getInventoryBySkuId(skuId));
    }
}
