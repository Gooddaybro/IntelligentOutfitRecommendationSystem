package com.recommendation.intelligentoutfitrecommendationsystem.inventory.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal 库存事实接口。
 *
 * Python AI 推荐服务调用该接口确认 SKU 是否有可用库存，避免推荐不可购买商品。
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
