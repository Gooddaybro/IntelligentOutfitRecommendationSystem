package com.recommendation.intelligentoutfitrecommendationsystem.favorite.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 当前用户收藏列表的商品展示投影。
 *
 * 该投影保留收藏关系对应的商品，即使商品已下架或缺货，前端仍能展示并删除它；
 * availabilityStatus 只描述当前可购买性，不改变收藏关系本身。
 */
@Data
public class FavoriteProduct {
    private Long spuId;
    private String name;
    private String categoryName;
    private String mainImageUrl;
    private BigDecimal salePrice;
    private String availabilityStatus;
    private Integer totalAvailableStock;
}
