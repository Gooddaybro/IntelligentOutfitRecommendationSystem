package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 推荐候选的 Redis 静态快照。
 *
 * 该类型故意不包含价格和库存；每次返回候选前必须从 MySQL 批量补齐实时交易事实。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidateSnapshot {
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
    private String skuCode;
    private String attributeTags;
}
