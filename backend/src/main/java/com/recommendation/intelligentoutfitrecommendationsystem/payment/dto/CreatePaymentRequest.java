package com.recommendation.intelligentoutfitrecommendationsystem.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 统一支付创建请求。
 *
 * <p>前端可以选择订单和支付渠道，但不能提交金额、用户 ID、支付状态或服务商交易号。
 * 这些信息由后端事务进行加载或生成。</p>
 */
public record CreatePaymentRequest(
        @NotBlank String orderNo,
        @NotBlank String channel
) {
}
