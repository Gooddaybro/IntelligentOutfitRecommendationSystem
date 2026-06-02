package com.recommendation.intelligentoutfitrecommendationsystem.order.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单数据访问入口。
 *
 * 查询接口必须以 userId 参与条件，确保公开订单 API 只能读写当前登录用户自己的订单；
 * 下单快照生成所需的商品事实数据由结算查询统一提供给 Service 层。
 */
@Mapper
public interface OrderMapper {

    void insertOrder(SalesOrder order);

    void insertItems(@Param("items") List<OrderItem> items);

    List<SalesOrder> findOrdersByUserId(@Param("userId") Long userId);

    SalesOrder findOrderByUserIdAndOrderNo(@Param("userId") Long userId, @Param("orderNo") String orderNo);

    List<OrderItem> findItemsByOrderId(@Param("orderId") Long orderId);

    /**
     * 读取当前用户选中的购物车项以及下单时刻的商品事实数据。
     *
     * @param userId 当前认证用户 ID，用于保护购物车归属边界
     * @param skuIds 前端选中结算的 SKU 集合，必须先在 Service 层去重和校验
     * @return 用于重算金额和生成订单快照的内部结算视图
     */
    List<OrderCheckoutItem> findCheckoutItemsFromCart(
            @Param("userId") Long userId,
            @Param("skuIds") List<Long> skuIds
    );
}
