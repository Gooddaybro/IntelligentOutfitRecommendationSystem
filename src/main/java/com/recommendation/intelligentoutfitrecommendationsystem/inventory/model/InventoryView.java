package com.recommendation.intelligentoutfitrecommendationsystem.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryView {
    private Long skuId;
    private String skuCode;
    private Long spuId;
    private String productName;
    private String color;
    private String size;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;
    private Boolean inStock;
}
