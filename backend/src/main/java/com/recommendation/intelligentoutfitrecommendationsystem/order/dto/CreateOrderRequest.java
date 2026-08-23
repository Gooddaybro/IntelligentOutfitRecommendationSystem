package com.recommendation.intelligentoutfitrecommendationsystem.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * 创建订单请求契约。
 *
 * 当前阶段只允许从购物车结算，前端只提交选中的 skuIds 和地址 ID；数量、单价
 * 和总金额都由后端从购物车和商品库重新读取，避免价格和数量被请求体篡改。
 */
public record CreateOrderRequest(
        @NotBlank
        String source,

        @NotEmpty
        List<@Positive Long> skuIds,

        @NotNull
        @Positive
        Long addressId
) {
}
