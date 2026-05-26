package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
