package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 下单时的不可变收货地址快照。
 *
 * {@code sourceAddressId} 保留当时的地址簿 ID 用于审计，对外仍按 {@code id}
 * 返回；展示历史订单时只依赖该记录中的文本，不回查可能已变更的地址簿。
 */
public record OrderAddressSnapshot(
        @JsonProperty("id") Long sourceAddressId,
        String recipientName,
        String phone,
        String province,
        String city,
        String district,
        String detail
) {
}
