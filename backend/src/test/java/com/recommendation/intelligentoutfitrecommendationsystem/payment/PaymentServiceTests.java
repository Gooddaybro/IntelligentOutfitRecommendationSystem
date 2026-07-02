package com.recommendation.intelligentoutfitrecommendationsystem.payment;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.CreatePaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper.PaymentMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentService;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.MockPaymentStrategy;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentStrategyRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private PaymentStrategyRegistry paymentStrategyRegistry;

    @Mock
    private BehaviorEventService behaviorEventService;

    @InjectMocks
    private PaymentService service;

    @Test
    void mockPayUnpaidOrderConfirmsStockCreatesPaymentAndMarksOrderPaid() {
        stubMockStrategy();
        SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(orderMapper.updateOrderPaid(eq(88L), any(LocalDateTime.class))).thenReturn(1);

        var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(paymentCaptor.capture());
        verify(orderMapper).updateOrderPaid(eq(88L), any(LocalDateTime.class));
        verify(behaviorEventService).recordBusinessEvent(argThat(command ->
                "PAYMENT_SUCCESS".equals(command.eventType())
                        && Long.valueOf(10L).equals(command.userId())
                        && command.orderNo().equals("ORDPAY1")
                        && Long.valueOf(1002L).equals(command.spuId())
                        && Long.valueOf(2102L).equals(command.skuId())
                        && Integer.valueOf(2).equals(command.quantity())
        ));
        assertThat(paymentCaptor.getValue().getPaymentNo()).startsWith("PAY");
        assertThat(paymentCaptor.getValue().getChannel()).isEqualTo("MOCK");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("SUCCESS");
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.paymentNo()).startsWith("PAY");
    }

    @Test
    void mockPayPaidOrderReturnsExistingPaymentWithoutMovingInventoryAgain() {
        SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "PAID");
        Payment payment = successPayment(88L, 10L, "ORDPAY1");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
        when(paymentMapper.findSuccessByOrderId(88L)).thenReturn(payment);

        var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

        verify(inventoryMapper, never()).confirmSoldStock(any(), any());
        verify(paymentMapper, never()).insertPayment(any(Payment.class));
        assertThat(response.paymentNo()).isEqualTo(payment.getPaymentNo());
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void mockPayRejectsCancelledOrderWithoutMovingInventory() {
        SalesOrder order = salesOrder(88L, 10L, "ORDCANCELLED1", "CANCELLED");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDCANCELLED1")).thenReturn(order);

        assertThatThrownBy(() -> service.mockPay(10L, new MockPaymentRequest("ORDCANCELLED1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("closed order cannot be paid: ORDCANCELLED1");
        verify(inventoryMapper, never()).confirmSoldStock(any(), any());
    }

    @Test
    void mockPayRejectsOrderNotOwnedByCurrentUser() {
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(11L, "ORDPRIVATE1")).thenReturn(null);

        assertThatThrownBy(() -> service.mockPay(11L, new MockPaymentRequest("ORDPRIVATE1")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("order not found: ORDPRIVATE1");
    }

    @Test
    void payWithMockChannelUsesUnifiedPathAndMarksOrderPaid() {
        stubMockStrategy();
        SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(orderMapper.updateOrderPaid(eq(88L), any(LocalDateTime.class))).thenReturn(1);

        PaymentResponse response = service.pay(10L, new CreatePaymentRequest("ORDPAY1", "MOCK"));

        verify(paymentMapper).insertPayment(any(Payment.class));
        verify(paymentMapper).markPaymentSuccess(
                eq(response.paymentNo()),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        );
        assertThat(response.channel()).isEqualTo("MOCK");
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void mockPayDelegatesToUnifiedPayPath() {
        stubMockStrategy();
        SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);
        when(orderMapper.updateOrderPaid(eq(88L), any(LocalDateTime.class))).thenReturn(1);

        PaymentResponse response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

        assertThat(response.channel()).isEqualTo("MOCK");
        verify(paymentMapper).insertPayment(any(Payment.class));
    }

    @Test
    void strategyRegistryReturnsMockStrategyByChannel() {
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(new MockPaymentStrategy()));

        assertThat(registry.getRequired("MOCK").channel()).isEqualTo("MOCK");
    }

    @Test
    void strategyRegistryRejectsUnsupportedChannel() {
        PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(new MockPaymentStrategy()));

        assertThatThrownBy(() -> registry.getRequired("ALIPAY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("unsupported payment channel: ALIPAY");
    }

    private SalesOrder salesOrder(Long id, Long userId, String orderNo, String status) {
        SalesOrder order = new SalesOrder();
        order.setId(id);
        order.setUserId(userId);
        order.setOrderNo(orderNo);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus(status);
        return order;
    }

    private OrderItem orderItem(Long skuId, int quantity) {
        OrderItem item = new OrderItem();
        item.setSkuId(skuId);
        item.setSpuId(1002L);
        item.setSkuCode("JK-COMMUTE-001-BLK-L");
        item.setSpuCode("JACKET_COMMUTE_001");
        item.setProductName("commute jacket");
        item.setCategoryName("jacket");
        item.setColor("black");
        item.setSize("L");
        item.setSalePrice(new BigDecimal("299.00"));
        item.setQuantity(quantity);
        item.setLineAmount(new BigDecimal("299.00").multiply(BigDecimal.valueOf(quantity)));
        return item;
    }

    private Payment successPayment(Long orderId, Long userId, String orderNo) {
        Payment payment = new Payment();
        payment.setPaymentNo("PAYEXISTING1");
        payment.setOrderId(orderId);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("299.00"));
        payment.setChannel("MOCK");
        payment.setStatus("SUCCESS");
        payment.setTransactionId("mock-transaction-existing");
        payment.setPaidAt(LocalDateTime.now());
        return payment;
    }

    private void stubMockStrategy() {
        when(paymentStrategyRegistry.getRequired("MOCK")).thenReturn(new MockPaymentStrategy());
    }
}
