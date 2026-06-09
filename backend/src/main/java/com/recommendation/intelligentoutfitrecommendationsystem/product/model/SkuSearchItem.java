package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * SKU 查询视图，承载颜色、尺码和售价等可购买规格信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuSearchItem {
    private Long skuId;
    private String skuCode;
    private Long spuId;
    private String productName;
    private String color;
    private String size;
    private BigDecimal salePrice;
    private String status;
}
