package com.recommendation.intelligentoutfitrecommendationsystem.favorite.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 当前用户收藏列表的商品展示投影。
 *
 * 该投影保留收藏关系对应的商品，即使商品已下架或缺货，前端仍能展示并删除它；
 * availabilityStatus 和 totalAvailableStock 只描述当前可购买性，不改变收藏关系本身。
 * salePrice 在可购买时是最低可购买 SKU 价格；不可购买时是仅供展示的历史目录最低价，
 * 无 SKU 的历史记录则为 null，不能被视为下单价格。
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
