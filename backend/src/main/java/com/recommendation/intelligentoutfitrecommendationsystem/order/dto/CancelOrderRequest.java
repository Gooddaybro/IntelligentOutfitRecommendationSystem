package com.recommendation.intelligentoutfitrecommendationsystem.order.dto;

/**
 * 取消订单请求契约。
 *
 * 该 DTO 只允许前端传递取消原因，订单归属和可取消状态必须由服务端根据 JWT 用户
 * 和订单行锁重新判断，避免用户通过请求体影响交易状态边界。
 */
public record CancelOrderRequest(String reason) {
}
