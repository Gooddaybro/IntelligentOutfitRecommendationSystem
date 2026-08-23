package com.recommendation.intelligentoutfitrecommendationsystem.checkout.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 一次服务端结算计算的不可变结果。
 *
 * 该结果是调用时点的价格和库存视图，不代表价格锁或库存锁；正式下单必须重新计算。
 */
public record CheckoutCalculation(
        List<CheckoutCalculatedItem> items,
        BigDecimal merchandiseAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        List<CheckoutInvalidReason> invalidReasons
) {

    public CheckoutCalculation {
        items = List.copyOf(items);
        invalidReasons = List.copyOf(invalidReasons);
    }
}
