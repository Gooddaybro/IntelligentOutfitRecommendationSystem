package com.recommendation.intelligentoutfitrecommendationsystem.cart.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目的持久化模型。
 *
 * cart_item 只保存用户、SKU 和数量关系；商品名称、价格、库存等可变展示信息由查询时
 * 从商品与库存表实时装配，避免购物车长期持有过期商品快照。
 */
@Data
public class CartItem {

    private Long id;

    private Long userId;

    private Long skuId;

    private Integer quantity;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
