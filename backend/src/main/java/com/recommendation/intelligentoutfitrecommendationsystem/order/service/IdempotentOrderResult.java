package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;

/**
 * 订单创建接口的幂等执行结果。
 *
 * replayed 只描述本次 HTTP 命令是否复用了既有订单，不进入订单领域状态。
 *
 * @param order 首次创建或重放得到的同一订单
 * @param replayed 是否复用了已经提交的幂等结果
 */
public record IdempotentOrderResult(OrderResponse order, boolean replayed) {
}
