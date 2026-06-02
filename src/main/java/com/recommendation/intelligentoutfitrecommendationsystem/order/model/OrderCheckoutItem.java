package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单创建时的内部结算视图。
 *
 * 该模型把当前用户购物车数量与商品、SKU、库存事实数据合并到一行，供 OrderService
 * 重算金额、校验上下架状态并生成订单快照；它不是对外 API 响应模型。
 */
@Data
public class OrderCheckoutItem {

    private Long skuId;

    private Long spuId;

    private String skuCode;

    private String spuCode;

    private String productName;

    private String categoryName;

    private String color;

    private String size;

    private BigDecimal salePrice;

    private Integer quantity;

    private String mainImageUrl;

    private String skuStatus;

    private String spuStatus;

    private Integer availableStock;
}
