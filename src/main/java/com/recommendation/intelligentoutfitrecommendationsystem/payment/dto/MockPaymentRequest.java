package com.recommendation.intelligentoutfitrecommendationsystem.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 模拟支付请求契约。
 *
 * 前端只能提交订单业务号，支付金额、渠道、用户归属和支付状态都由后端在事务内重新读取
 * 和生成，避免请求体篡改交易事实。
 */
public record MockPaymentRequest(
        @NotBlank String orderNo
) {
}
