package com.recommendation.intelligentoutfitrecommendationsystem.aftersale.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后申请响应。
 *
 * 响应只暴露业务单号和状态，不暴露数据库自增主键；金额来自订单快照，便于后续退款阶段
 * 复用同一售后记录。
 */
public record AfterSaleResponse(
        String requestNo,
        String orderNo,
        String type,
        String reason,
        String status,
        BigDecimal refundAmount,
        String handlerNote,
        LocalDateTime handledAt,
        LocalDateTime createdAt
) {
}
