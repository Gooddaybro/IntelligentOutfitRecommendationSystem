package com.recommendation.intelligentoutfitrecommendationsystem.payment.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付回调审计记录。
 *
 * <p>该模型存储来自支付服务商的原始回调数据，用于故障排查和幂等性分析。
 * 保存回调日志并不意味着该支付已获信任；在变更订单或库存状态之前，服务仍须验证
 * 渠道签名、金额、订单归属及支付状态。</p>
 */
@Data
public class PaymentCallbackLog {

    private Long id;

    private String channel;

    private String paymentNo;

    private String orderNo;

    private String providerTradeNo;

    private String eventType;

    private String rawBody;

    private String headers;

    private Boolean signatureValid;

    private Boolean handled;

    private String failureReason;

    private LocalDateTime createdAt;
}
