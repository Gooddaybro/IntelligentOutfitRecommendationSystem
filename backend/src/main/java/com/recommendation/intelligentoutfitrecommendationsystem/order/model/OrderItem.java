package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细持久化模型。
 *
 * 明细行保存下单时刻的商品与价格快照，历史订单展示只依赖该表，不依赖当前商品目录
 * 中可能已经变化的名称、图片、规格或销售价格。
 */
@Data
public class OrderItem {

    private Long id;

    private Long orderId;

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

    private BigDecimal lineAmount;

    private String mainImageUrl;

    private LocalDateTime createdAt;
}
