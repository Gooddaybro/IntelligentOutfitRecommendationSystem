package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单模块面向支付和售后的应用边界。
 *
 * <p>跨模块调用方只能获得完成交易协作所需的不可变事实，不能直接访问订单 Mapper
 * 或把订单持久化模型当作公共契约。事务仍由外层业务服务建立，行锁会参与同一事务。</p>
 */
@Service
public class OrderApplicationService {

    private final OrderMapper orderMapper;

    public OrderApplicationService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * 按认证用户和订单号锁定订单，并转换为跨模块只读视图。
     *
     * @return 订单不存在或不属于当前用户时返回 null
     */
    public OrderView lockOwnedOrder(Long userId, String orderNo) {
        return toView(orderMapper.findOrderByUserIdAndOrderNoForUpdate(userId, orderNo));
    }

    /**
     * 按系统订单号锁定订单，供无用户 JWT 的可信支付回调使用。
     *
     * @return 订单不存在时返回 null，由支付模块记录拒绝原因而不是抛出公开接口异常
     */
    public OrderView lockOrder(String orderNo) {
        return toView(orderMapper.findOrderByOrderNoForUpdate(orderNo));
    }

    /**
     * 返回支付确认库存与行为事件所需的最小订单明细事实。
     */
    public List<OrderItemView> findItems(Long orderId) {
        return orderMapper.findItemsByOrderId(orderId).stream()
                .map(this::toItemView)
                .toList();
    }

    /**
     * 将未支付订单更新为已支付，并在条件更新失败时统一解释并发状态变化。
     */
    public void markPaid(OrderView order, LocalDateTime paidAt) {
        int affectedRows = orderMapper.updateOrderPaid(order.id(), paidAt);
        if (affectedRows == 0) {
            throw new BadRequestException("order status changed before payment: " + order.orderNo());
        }
    }

    private OrderView toView(SalesOrder order) {
        if (order == null) {
            return null;
        }
        return new OrderView(
                order.getId(),
                order.getUserId(),
                order.getOrderNo(),
                order.getTotalAmount(),
                order.getStatus()
        );
    }

    private OrderItemView toItemView(OrderItem item) {
        return new OrderItemView(item.getSkuId(), item.getSpuId(), item.getQuantity());
    }

    /**
     * 支付和售后协作所需的订单主表事实，不包含可变持久化对象。
     */
    public record OrderView(Long id, Long userId, String orderNo, BigDecimal totalAmount, String status) {
    }

    /**
     * 支付成功后确认库存和记录行为事件所需的订单明细事实。
     */
    public record OrderItemView(Long skuId, Long spuId, Integer quantity) {
    }
}
