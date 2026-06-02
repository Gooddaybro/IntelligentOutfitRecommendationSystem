package com.recommendation.intelligentoutfitrecommendationsystem.cart.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 购物车列表的展示视图。
 *
 * 该模型代表用户购物车和商品事实数据的只读组合，供公开购物车 API 返回给前端。
 * 它不保存订单成交价格，订单模块创建订单时需要重新生成独立的订单明细快照。
 */
@Data
public class CartItemView {

    private Long id;

    private Long userId;

    private Long skuId;

    private Long spuId;

    private String skuCode;

    private String spuCode;

    private String name;

    private String categoryName;

    private String color;

    private String size;

    private BigDecimal salePrice;

    private String stockStatus;

    private String mainImageUrl;

    private Integer quantity;

    private Integer availableStock;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
