package com.recommendation.intelligentoutfitrecommendationsystem.checkout.model;

import java.math.BigDecimal;

/**
 * 服务端结算后的不可变商品行。
 *
 * 数量来自当前用户购物车，价格与展示快照来自 Java 商品事实库；客户端不能构造这些字段。
 */
public record CheckoutCalculatedItem(
        Long skuId,
        Long spuId,
        String skuCode,
        String spuCode,
        String name,
        String categoryName,
        String color,
        String size,
        BigDecimal salePrice,
        Integer quantity,
        BigDecimal lineAmount,
        String mainImageUrl
) {
}
