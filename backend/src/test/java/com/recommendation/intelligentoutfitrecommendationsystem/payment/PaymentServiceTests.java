package com.recommendation.intelligentoutfitrecommendationsystem.payment;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderItemView;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderApplicationService.OrderView;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.CreatePaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.PaymentResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.MockPaymentRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.mapper.PaymentMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.dto.ProviderPaymentCallback;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.model.Payment;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentCallbackVerification;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentCallbackVerifier;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.service.PaymentService;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.MockPaymentStrategy;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentResult;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentRequestContext;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentStrategy;
import com.recommendation.intelligentoutfitrecommendationsystem.payment.strategy.PaymentStrategyRegistry;
import org.springframework.mock.web.MockHttpServletRequest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private OrderApplicationService orderApplicationService;

    @Mock
    private InventoryApplicationService inventoryApplicationService;

    @Mock
    private PaymentStrategyRegistry paymentStrategyRegistry;

    @Mock
    private BehaviorEventService behaviorEventService;

    @Mock
    private PaymentCallbackVerifier paymentCallbackVerifier;

    @InjectMocks
    private PaymentService service;

    @Test
    void mockPayUnpaidOrderConfirmsStockCreatesPaymentAndMarksOrderPaid() {
        stubMockStrategy();
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);
        when(orderApplicationService.findItems(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);

        var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(paymentCaptor.capture());
        verify(orderApplicationService).markPaid(eq(order), any(LocalDateTime.class));
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
        OrderView order = order(88L, 10L, "ORDPAY1", "PAID");
        Payment payment = successPayment(88L, 10L, "ORDPAY1");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);
        when(paymentMapper.findSuccessByOrderId(88L)).thenReturn(payment);

        var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

        verify(inventoryApplicationService, never()).confirm(any(), any());
        verify(paymentMapper, never()).insertPayment(any(Payment.class));
        assertThat(response.paymentNo()).isEqualTo(payment.getPaymentNo());
        assertThat(response.status()).isEqualTo("SUCCESS");
    }

    @Test
    void mockPayRejectsCancelledOrderWithoutMovingInventory() {
        OrderView order = order(88L, 10L, "ORDCANCELLED1", "CANCELLED");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDCANCELLED1")).thenReturn(order);

        assertThatThrownBy(() -> service.mockPay(10L, new MockPaymentRequest("ORDCANCELLED1")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("closed order cannot be paid: ORDCANCELLED1");
        verify(inventoryApplicationService, never()).confirm(any(), any());
    }

    @Test
    void mockPayRejectsOrderNotOwnedByCurrentUser() {
        when(orderApplicationService.lockOwnedOrder(11L, "ORDPRIVATE1")).thenReturn(null);

        assertThatThrownBy(() -> service.mockPay(11L, new MockPaymentRequest("ORDPRIVATE1")))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("order not found: ORDPRIVATE1");
    }

    @Test
    void payWithMockChannelUsesUnifiedPathAndMarksOrderPaid() {
        stubMockStrategy();
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);
        when(orderApplicationService.findItems(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);

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
    void payWithProviderChannelCreatesPendingPaymentWithoutMovingInventory() {
        when(paymentStrategyRegistry.getRequired("ALIPAY")).thenReturn(new PaymentStrategy() {
            @Override
            public String channel() {
                return "ALIPAY";
            }

            @Override
            public PaymentResult pay(PaymentRequestContext context) {
                return new PaymentResult(
                        context.paymentNo(),
                        context.channel(),
                        "PENDING",
                        "ALI-PENDING-1",
                        null,
                        "{\"checkoutUrl\":\"https://pay.example.test/alipay\"}",
                        null
                );
            }
        });
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);

        PaymentResponse response = service.pay(10L, new CreatePaymentRequest("ORDPAY1", "ALIPAY"));

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentMapper).insertPayment(paymentCaptor.capture());
        verify(inventoryApplicationService, never()).confirm(any(), any());
        verify(orderApplicationService, never()).markPaid(any(), any());
        assertThat(response.channel()).isEqualTo("ALIPAY");
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(paymentCaptor.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(paymentCaptor.getValue().getProviderPayload()).contains("checkoutUrl");
    }

    @Test
    void payUnpaidOrderWithExistingPendingPaymentReturnsExistingPaymentWithoutStartingNewProviderAttempt() {
        PaymentStrategy alipayStrategy = mock(PaymentStrategy.class);
        when(paymentStrategyRegistry.getRequired("ALIPAY")).thenReturn(alipayStrategy);
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        Payment pendingPayment = pendingPayment(88L, 10L, "ORDPAY1", "ALIPAY");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);
        when(paymentMapper.findPendingByOrderId(88L)).thenReturn(pendingPayment);

        PaymentResponse response = service.pay(10L, new CreatePaymentRequest("ORDPAY1", "ALIPAY"));

        verify(alipayStrategy, never()).pay(any(PaymentRequestContext.class));
        verify(paymentMapper, never()).insertPayment(any(Payment.class));
        verify(inventoryApplicationService, never()).confirm(any(), any());
        verify(orderApplicationService, never()).markPaid(any(), any());
        assertThat(response.paymentNo()).isEqualTo("PAYPENDING1");
        assertThat(response.status()).isEqualTo("PENDING");
    }

    @Test
    void signedProviderCallbackConfirmsPendingPaymentThroughUnifiedSuccessPath() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String rawBody = "{\"paymentNo\":\"PAYPENDING1\"}";
        ProviderPaymentCallback callback = providerCallback("PAYPENDING1", "ORDPAY1", "SUCCESS");
        Payment payment = pendingPayment(88L, 10L, "ORDPAY1", "ALIPAY");
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        when(paymentCallbackVerifier.verify("ALIPAY", rawBody, request))
                .thenReturn(PaymentCallbackVerification.valid(callback));
        when(paymentMapper.findByPaymentNoForUpdate("PAYPENDING1")).thenReturn(payment);
        when(orderApplicationService.lockOrder("ORDPAY1")).thenReturn(order);
        when(orderApplicationService.findItems(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(paymentMapper.markPaymentSuccess(
                eq("PAYPENDING1"),
                eq("ALI-TRADE-1"),
                eq("ALI-TX-1"),
                eq(callback.paidAt())
        )).thenReturn(1);

        service.handleCallback("ALIPAY", rawBody, request);

        verify(paymentMapper).insertCallbackLog(argThat(log ->
                Boolean.TRUE.equals(log.getSignatureValid())
                        && Boolean.TRUE.equals(log.getHandled())
                        && "PAYMENT_SUCCESS".equals(log.getEventType())
                        && "PAYPENDING1".equals(log.getPaymentNo())
        ));
        verify(inventoryApplicationService).confirm(2102L, 2);
        verify(orderApplicationService).markPaid(order, callback.paidAt());
    }

    @Test
    void duplicateProviderSuccessCallbackIsAuditedButDoesNotConfirmStockAgain() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String rawBody = "{\"paymentNo\":\"PAYPENDING1\"}";
        ProviderPaymentCallback callback = providerCallback("PAYPENDING1", "ORDPAY1", "SUCCESS");
        Payment payment = successPayment(88L, 10L, "ORDPAY1");
        payment.setPaymentNo("PAYPENDING1");
        payment.setChannel("ALIPAY");
        when(paymentCallbackVerifier.verify("ALIPAY", rawBody, request))
                .thenReturn(PaymentCallbackVerification.valid(callback));
        when(paymentMapper.findByPaymentNoForUpdate("PAYPENDING1")).thenReturn(payment);

        service.handleCallback("ALIPAY", rawBody, request);

        verify(inventoryApplicationService, never()).confirm(any(), any());
        verify(orderApplicationService, never()).markPaid(any(), any());
        verify(paymentMapper).insertCallbackLog(argThat(log ->
                Boolean.TRUE.equals(log.getSignatureValid())
                        && Boolean.TRUE.equals(log.getHandled())
                        && "DUPLICATE_PAYMENT_SUCCESS".equals(log.getEventType())
        ));
    }

    @Test
    void invalidProviderCallbackSignatureIsAuditedWithoutChangingPaymentState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String rawBody = "{\"paymentNo\":\"PAYPENDING1\"}";
        when(paymentCallbackVerifier.verify("ALIPAY", rawBody, request))
                .thenReturn(PaymentCallbackVerification.invalid("invalid signature"));

        service.handleCallback("ALIPAY", rawBody, request);

        verify(paymentMapper, never()).findByPaymentNoForUpdate(any());
        verify(inventoryApplicationService, never()).confirm(any(), any());
        verify(paymentMapper).insertCallbackLog(argThat(log ->
                Boolean.FALSE.equals(log.getSignatureValid())
                        && Boolean.FALSE.equals(log.getHandled())
                        && "CALLBACK_REJECTED".equals(log.getEventType())
                        && "invalid signature".equals(log.getFailureReason())
        ));
    }

    @Test
    void mockPayDelegatesToUnifiedPayPath() {
        stubMockStrategy();
        OrderView order = order(88L, 10L, "ORDPAY1", "UNPAID");
        when(orderApplicationService.lockOwnedOrder(10L, "ORDPAY1")).thenReturn(order);
        when(orderApplicationService.findItems(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(paymentMapper.markPaymentSuccess(
                any(String.class),
                any(String.class),
                any(String.class),
                any(LocalDateTime.class)
        )).thenReturn(1);

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

    private OrderView order(Long id, Long userId, String orderNo, String status) {
        return new OrderView(id, userId, orderNo, new BigDecimal("299.00"), status);
    }

    private OrderItemView orderItem(Long skuId, int quantity) {
        return new OrderItemView(skuId, 1002L, quantity);
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

    private Payment pendingPayment(Long orderId, Long userId, String orderNo, String channel) {
        Payment payment = new Payment();
        payment.setPaymentNo("PAYPENDING1");
        payment.setOrderId(orderId);
        payment.setOrderNo(orderNo);
        payment.setUserId(userId);
        payment.setAmount(new BigDecimal("299.00"));
        payment.setChannel(channel);
        payment.setStatus("PENDING");
        return payment;
    }

    private ProviderPaymentCallback providerCallback(String paymentNo, String orderNo, String status) {
        return new ProviderPaymentCallback(
                paymentNo,
                orderNo,
                new BigDecimal("299.00"),
                status,
                "ALI-TRADE-1",
                "ALI-TX-1",
                "{\"provider\":\"ALIPAY\"}",
                LocalDateTime.of(2026, 7, 5, 21, 0)
        );
    }

    private void stubMockStrategy() {
        when(paymentStrategyRegistry.getRequired("MOCK")).thenReturn(new MockPaymentStrategy());
    }
}
