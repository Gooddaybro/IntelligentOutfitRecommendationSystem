package com.recommendation.intelligentoutfitrecommendationsystem.order.model;

/**
 * 订单创建幂等操作域。
 *
 * 操作域参与数据库唯一约束和请求摘要，使购物车结算与立即购买可以安全使用同一个
 * 客户端 UUID，同时避免两个不同订单命令共享幂等结果。
 */
public enum OrderOperation {
    CART_CHECKOUT,
    BUY_NOW
}
