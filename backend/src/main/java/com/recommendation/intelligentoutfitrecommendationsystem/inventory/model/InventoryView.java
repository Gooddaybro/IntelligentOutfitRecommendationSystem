package com.recommendation.intelligentoutfitrecommendationsystem.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 库存查询视图，汇总 SKU 基础信息和可售、锁定、售出库存状态。
 */
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
