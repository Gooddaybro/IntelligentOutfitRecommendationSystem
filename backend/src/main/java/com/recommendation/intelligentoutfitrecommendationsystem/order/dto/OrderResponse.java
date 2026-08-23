package com.recommendation.intelligentoutfitrecommendationsystem.order.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderAddressSnapshot;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单响应契约。
 *
 * 该 DTO 同时用于创建订单后的结果和订单详情读取，始终只暴露业务单号 orderNo，
 * 不暴露数据库自增主键；address 定义详情响应的快照槽位，列表查询不联表。
 * 关闭字段用于表达取消或超时关闭边界，不承载支付流水细节。
 */
public record OrderResponse(
        String orderNo,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        OrderAddressSnapshot address,
        LocalDateTime createdAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt,
        String closeReason
) {
}
