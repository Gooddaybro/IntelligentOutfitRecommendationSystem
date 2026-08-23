package com.recommendation.intelligentoutfitrecommendationsystem.checkout.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculatedItem;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutCalculation;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutInvalidReason;

import java.math.BigDecimal;
import java.util.List;

/**
 * 公开结算预览响应。
 *
 * 所有金额和商品行由服务端计算；响应只表达当前时点，不生成价格承诺或库存预留。
 */
public record CheckoutPreviewResponse(
        List<CheckoutCalculatedItem> items,
        BigDecimal merchandiseAmount,
        BigDecimal shippingAmount,
        BigDecimal discountAmount,
        BigDecimal payableAmount,
        List<String> invalidReasons
) {

    public static CheckoutPreviewResponse from(CheckoutCalculation calculation) {
        return new CheckoutPreviewResponse(
                calculation.items(),
                calculation.merchandiseAmount(),
                calculation.shippingAmount(),
                calculation.discountAmount(),
                calculation.payableAmount(),
                calculation.invalidReasons().stream()
                        .map(CheckoutInvalidReason::message)
                        .toList()
        );
    }
}
