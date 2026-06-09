package com.recommendation.intelligentoutfitrecommendationsystem.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 购物车数量更新入参。
 *
 * SKU 放在路径中，数量放在请求体中，保持“定位资源”和“修改状态”的 API 语义分离。
 */
public record UpdateCartItemRequest(
        @NotNull
        @Min(1)
        @Max(99)
        Integer quantity
) {
}
