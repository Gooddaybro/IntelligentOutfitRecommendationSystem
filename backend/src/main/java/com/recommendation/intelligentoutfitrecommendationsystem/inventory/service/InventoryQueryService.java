package com.recommendation.intelligentoutfitrecommendationsystem.inventory.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.springframework.stereotype.Service;

/**
 * 库存查询服务，统一校验 SKU 入参并把缺失库存转换为业务异常。
 */
@Service
public class InventoryQueryService {

    private final InventoryMapper inventoryMapper;

    public InventoryQueryService(InventoryMapper inventoryMapper) {
        this.inventoryMapper = inventoryMapper;
    }

    public InventoryView getInventoryBySkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        InventoryView inventory = inventoryMapper.findBySkuId(skuId);
        if (inventory == null) {
            throw new ResourceNotFoundException("inventory not found for sku: " + skuId);
        }
        return inventory;
    }
}
