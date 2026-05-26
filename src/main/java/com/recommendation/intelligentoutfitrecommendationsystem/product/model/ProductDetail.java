package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetail {
    private Long spuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String description;
    private String mainImageUrl;
    private String fitType;
    private List<String> materials;
    private List<String> seasons;
    private List<String> styleTags;
    private Map<String, String> attributes;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
