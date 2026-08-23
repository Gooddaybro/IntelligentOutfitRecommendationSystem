package com.recommendation.intelligentoutfitrecommendationsystem.checkout.model;

/**
 * 结算时点发现的可解释业务无效原因。
 *
 * 预览接口把它转换为用户可读文本，正式下单准备入口则将同一原因转换为阻断异常。
 */
public record CheckoutInvalidReason(
        String code,
        Long skuId,
        String message
) {
}
