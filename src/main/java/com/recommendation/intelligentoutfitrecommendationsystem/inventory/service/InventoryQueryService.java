package com.recommendation.intelligentoutfitrecommendationsystem.inventory.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.repository.InventoryQueryRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryQueryService {

    private final InventoryQueryRepository inventoryQueryRepository;

    public InventoryQueryService(InventoryQueryRepository inventoryQueryRepository) {
        this.inventoryQueryRepository = inventoryQueryRepository;
    }

    public InventoryView getInventoryBySkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        return inventoryQueryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found for sku: " + skuId));
    }
}
