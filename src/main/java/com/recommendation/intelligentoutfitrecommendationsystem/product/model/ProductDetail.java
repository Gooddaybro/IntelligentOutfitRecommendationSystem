package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品详情视图，聚合 SPU 基础信息、标签、属性和价格区间。
 */
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
