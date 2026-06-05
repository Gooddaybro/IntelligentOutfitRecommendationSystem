package com.recommendation.intelligentoutfitrecommendationsystem.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 立即购买请求契约。
 *
 * 该请求只表达当前登录用户对单个 SKU 的购买意图，不接收金额、订单状态或 userId；
 * 价格、商品状态和库存事实必须由后端在下单事务内重新读取。
 */
public record BuyNowRequest(
        @NotNull(message = "商品sku不能为空")
        @Positive(message = "非法商品sku")
        Long skuId,

        @NotNull(message = "购买数量不能为空")
        @Positive(message = "购买数量必须>0")
        @Max(value=99,message = "单次购买数量最多99件")
        Integer quantity
) {
}
