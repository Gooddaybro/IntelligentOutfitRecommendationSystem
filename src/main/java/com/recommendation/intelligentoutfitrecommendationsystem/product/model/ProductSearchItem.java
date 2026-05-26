package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchItem {
    private Long spuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String mainImageUrl;
    private String fitType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
