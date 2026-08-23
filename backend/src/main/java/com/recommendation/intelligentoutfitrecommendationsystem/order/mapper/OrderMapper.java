package com.recommendation.intelligentoutfitrecommendationsystem.order.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderAddressSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.BuyNowCheckoutItem;
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
 * 购物车下单事实由 checkout 模块提供，本 Mapper 只保留立即购买所需的最小商品投影。
 */
@Mapper
public interface OrderMapper {

    void insertOrder(SalesOrder order);

    void insertItems(@Param("items") List<OrderItem> items);

    /**
     * 为订单固化下单时的收货地址。
     *
     * @param orderId 已创建订单的内部 ID
     * @param snapshot 与可变地址簿脱钩的完整地址文本
     */
    void insertAddressSnapshot(
            @Param("orderId") Long orderId,
            @Param("snapshot") OrderAddressSnapshot snapshot
    );

    /**
     * 按订单读取历史收货地址，仅供已通过订单归属校验的详情流程使用。
     *
     * @param orderId 已校验归属的订单内部 ID
     * @return 下单时地址快照；未写入时返回 null
     */
    OrderAddressSnapshot findAddressSnapshotByOrderId(@Param("orderId") Long orderId);

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
     * 按 SKU 读取立即购买所需的商品事实快照。
     *
     * @param skuId 前端选择的 SKU，数量和用户边界不由 SQL 决定
     * @return 用于后端重算金额和生成订单明细的结算视图；SKU 不存在时返回 null
     */
    BuyNowCheckoutItem findCheckoutItemBySkuId(@Param("skuId") Long skuId);
}
