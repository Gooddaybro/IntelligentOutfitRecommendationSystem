package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * MySQL 返回的推荐候选实时交易事实。
 *
 * 查询只返回当前仍在售的 SPU/SKU；调用方还必须过滤 availableStock 不大于零的记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidateLiveFact {
    private Long skuId;
    private BigDecimal salePrice;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer totalAvailableStock;
    private Integer availableStock;
}
