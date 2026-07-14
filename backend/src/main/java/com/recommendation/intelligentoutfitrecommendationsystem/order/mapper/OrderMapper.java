package com.recommendation.intelligentoutfitrecommendationsystem.order.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
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

    SalesOrder findOrderByUserIdAndId(@Param("userId") Long userId, @Param("orderId") Long orderId);

    /**
     * 按当前用户和订单号锁定订单主表行。
     *
     * @param userId 当前认证用户 ID，用于保护公开订单接口的用户隔离边界
     * @param orderNo 前端持有的订单业务号
     * @return 被当前事务锁定的订单；不存在或不属于当前用户时返回 null
     */
    SalesOrder findOrderByUserIdAndOrderNoForUpdate(@Param("userId") Long userId, @Param("orderNo") String orderNo);

    /**
     * 系统内部按订单号锁定订单主表行。
     *
     * @param orderNo 系统超时任务扫描到的订单业务号
     * @return 被当前事务锁定的订单；不存在时返回 null
     */
    SalesOrder findOrderByOrderNoForUpdate(@Param("orderNo") String orderNo);

    List<OrderItem> findItemsByOrderId(@Param("orderId") Long orderId);

    int updateOrderClosed(
            @Param("orderId") Long orderId,
            @Param("status") String status,
            @Param("closeReason") String closeReason
    );

    int updateOrderPaid(@Param("orderId") Long orderId, @Param("paidAt") LocalDateTime paidAt);

    List<String> findExpiredUnpaidOrderNos(
            @Param("cutoffTime") LocalDateTime cutoffTime,
            @Param("batchSize") int batchSize
    );

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

    /**
     * 按 SKU 读取立即购买所需的商品事实快照。
     *
     * @param skuId 前端选择的 SKU，数量和用户边界不由 SQL 决定
     * @return 用于后端重算金额和生成订单明细的结算视图；SKU 不存在时返回 null
     */
    OrderCheckoutItem findCheckoutItemBySkuId(@Param("skuId") Long skuId);
}
