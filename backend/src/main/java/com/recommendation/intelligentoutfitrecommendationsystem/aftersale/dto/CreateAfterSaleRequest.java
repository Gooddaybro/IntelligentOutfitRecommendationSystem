package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 当前用户发起售后申请的请求契约。
 *
 * 前端只能提交订单号、售后类型和原因；退款金额、用户归属和目标状态必须由 Java
 * 根据订单快照重新计算。
 */
public record CreateAfterSaleRequest(
        @NotBlank String orderNo,
        @NotBlank @Pattern(regexp = "^(REFUND|RETURN_REFUND)$") String type,
        @NotBlank @Size(max = 255) String reason
) {
}
