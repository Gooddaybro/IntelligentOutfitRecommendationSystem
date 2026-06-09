package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 推荐候选商品视图。
 *
 * materials、seasons、styleTags 使用逗号拼接，方便 Python AI 服务快速读取候选摘要。
 * 商品详情接口仍返回结构化集合，适合前端展示和更细粒度解释。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidate {
    private Long spuId;
    private Long skuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String mainImageUrl;
    private String fitType;
    private String color;
    private String size;
    private String materials;
    private String seasons;
    private String styleTags;
    private BigDecimal salePrice;
    private String stockStatus;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer totalAvailableStock;
}
