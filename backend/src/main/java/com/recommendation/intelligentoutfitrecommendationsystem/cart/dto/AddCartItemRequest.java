package com.recommendation.intelligentoutfitrecommendationsystem.cart.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 加购接口入参。
 *
 * 请求体不接收 userId，购物车归属只能由 JWT 认证上下文决定。可选 recommendationId
 * 只用于推荐归因，不参与商品、数量或库存判断。
 */
public record AddCartItemRequest(
        @NotNull
        @Min(1)
        Long skuId,

        @NotNull
        @Min(1)
        @Max(99)
        Integer quantity,

        String recommendationId
) {
    public AddCartItemRequest(Long skuId, Integer quantity) {
        this(skuId, quantity, null);
    }
}
