package com.recommendation.intelligentoutfitrecommendationsystem.payment.service;

import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.ProviderPaymentCallback;

/**
 * 支付回调验签结果。
 *
 * 使用显式结果对象而不是抛异常，可以让公开回调入口统一返回 received，同时把失败原因
 * 写入审计日志，不向服务商或攻击者暴露内部订单状态。
 */
public record PaymentCallbackVerification(
        boolean valid,
        ProviderPaymentCallback callback,
        String failureReason
) {

    public static PaymentCallbackVerification valid(ProviderPaymentCallback callback) {
        return new PaymentCallbackVerification(true, callback, null);
    }

    public static PaymentCallbackVerification invalid(String failureReason) {
        return new PaymentCallbackVerification(false, null, failureReason);
    }
}
