package com.recommendation.intelligentoutfitrecommendationsystem.inventory.model;

public record InventoryView(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        Integer availableStock,
        Integer lockedStock,
        Integer soldStock,
        Boolean inStock
) {
}
