package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;

/**
 * 单次订单创建事务的内部结果。
 *
 * 数据库主键只供幂等记录建立可靠引用，公开 API 仍只暴露业务订单号。
 *
 * @param orderId 新订单数据库主键
 * @param order 可返回给当前用户的订单响应
 */
public record OrderCreationResult(Long orderId, OrderResponse order) {
}
