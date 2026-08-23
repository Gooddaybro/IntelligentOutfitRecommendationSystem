package com.recommendation.intelligentoutfitrecommendationsystem.checkout.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 结算预览的客户端输入边界。
 *
 * 只接受商品选择和地址；Jackson 忽略的未知金额或数量字段不会进入任何计算路径。
 */
public record CheckoutPreviewRequest(
        @NotEmpty List<@Positive Long> skuIds,
        @NotNull @Positive Long addressId
) {
}
