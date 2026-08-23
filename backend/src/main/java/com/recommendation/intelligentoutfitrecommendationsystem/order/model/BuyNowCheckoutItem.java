package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 立即购买创建订单时的服务端商品事实投影。
 *
 * 该类型不承载购物车结算；数量来自立即购买请求，商品、价格和状态来自 Java 商品事实库。
 */
@Data
public class BuyNowCheckoutItem {

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
}
