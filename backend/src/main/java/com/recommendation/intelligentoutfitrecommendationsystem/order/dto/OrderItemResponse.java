package com.recommendation.intelligentoutfitrecommendationsystem.order.dto;

import java.math.BigDecimal;

/**
 * 订单明细响应契约。
 *
 * 返回的是 order_item 中固化的交易快照，而不是当前商品表实时数据，保证历史订单展示
 * 不受后续商品改名、改价或下架影响。
 */
public record OrderItemResponse(
        Long skuId,
        Long spuId,
        String skuCode,
        String spuCode,
        String productName,
        String categoryName,
        String color,
        String size,
        BigDecimal salePrice,
        Integer quantity,
        BigDecimal lineAmount,
        String mainImageUrl
) {
}
