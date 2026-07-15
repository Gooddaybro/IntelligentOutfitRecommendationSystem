package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderApplicationServiceTests {

    private final OrderMapper orderMapper = mock(OrderMapper.class);

    private final OrderApplicationService service = new OrderApplicationService(orderMapper);

    @Test
    void lockOwnedOrderReturnsImmutableCrossModuleView() {
        SalesOrder order = order(88L, 10L, "ORD1", "UNPAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORD1")).thenReturn(order);

        OrderApplicationService.OrderView result = service.lockOwnedOrder(10L, "ORD1");

        assertThat(result).isEqualTo(new OrderApplicationService.OrderView(
                88L, 10L, "ORD1", new BigDecimal("299.00"), "UNPAID"));
    }

    @Test
    void lockSystemOrderPreservesMissingResultForCallbackAudit() {
        when(orderMapper.findOrderByOrderNoForUpdate("ORD-MISSING")).thenReturn(null);

        assertThat(service.lockOrder("ORD-MISSING")).isNull();
    }

    @Test
    void findItemsReturnsOnlyPaymentRequiredFacts() {
        OrderItem item = new OrderItem();
        item.setSkuId(7L);
        item.setSpuId(3L);
        item.setQuantity(2);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(item));

        assertThat(service.findItems(88L)).containsExactly(
                new OrderApplicationService.OrderItemView(7L, 3L, 2));
    }

    @Test
    void markPaidExplainsConcurrentOrderStatusChange() {
        LocalDateTime paidAt = LocalDateTime.of(2026, 7, 15, 11, 45);
        OrderApplicationService.OrderView order = new OrderApplicationService.OrderView(
                88L, 10L, "ORD1", new BigDecimal("299.00"), "UNPAID");
        when(orderMapper.updateOrderPaid(88L, paidAt)).thenReturn(0);

        assertThatThrownBy(() -> service.markPaid(order, paidAt))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("order status changed before payment: ORD1");

        verify(orderMapper).updateOrderPaid(88L, paidAt);
    }

    private SalesOrder order(Long id, Long userId, String orderNo, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus(status);
        return order;
    }
}
