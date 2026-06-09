package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未支付订单超时关闭调度器。
 *
 * 调度器只负责按配置扫描候选订单并逐个委托 OrderService；真正的订单行锁、状态复查和
 * 库存释放仍在 Service 事务中完成，避免定时任务绕过交易一致性边界。
 */
@Component
public class OrderTimeoutScheduler {

    private final OrderService orderService;

    private final OrderTimeoutProperties properties;

    public OrderTimeoutScheduler(OrderService orderService, OrderTimeoutProperties properties) {
        this.orderService = orderService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${order.timeout-close-fixed-delay-ms:60000}")
    public void closeExpiredOrders() {
        int timeoutMinutes = properties.getUnpaidTimeoutMinutes();
        List<String> orderNos = orderService.findExpiredUnpaidOrderNos(
                timeoutMinutes,
                properties.getTimeoutCloseBatchSize()
        );
        String reason = "TIMEOUT_UNPAID_" + timeoutMinutes + "_MINUTES";
        for (String orderNo : orderNos) {
            orderService.closeExpiredOrder(orderNo, reason);
        }
    }
}
