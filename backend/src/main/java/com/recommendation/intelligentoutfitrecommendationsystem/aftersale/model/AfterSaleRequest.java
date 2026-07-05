package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后申请持久化模型。
 *
 * 该模型显式记录售后状态机，为后续真实退款、退货和运营审核保留边界；本阶段不会直接
 * 修改支付流水或库存。
 */
@Data
public class AfterSaleRequest {

    private Long id;

    private String requestNo;

    private Long orderId;

    private String orderNo;

    private Long userId;

    private String type;

    private String reason;

    private String status;

    private BigDecimal refundAmount;

    private String handlerNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
