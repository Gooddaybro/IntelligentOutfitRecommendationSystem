package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.BuyNowRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CancelOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    @Mock
    private OrderMapper orderMapper;

    @Mock
    private InventoryMapper inventoryMapper;

    @Mock
    private CartService cartService;

    @InjectMocks
    private OrderService service;

    @Test
    void createOrderFromCartRecalculatesAmountLocksStockAndStoresSnapshots() {
        var request = new CreateOrderRequest("CART", List.of(2102L, 2202L));
        when(orderMapper.findCheckoutItemsFromCart(10L, List.of(2102L, 2202L)))
                .thenReturn(List.of(checkoutItem(2102L, "299.00", 1), checkoutItem(2202L, "199.00", 2)));
        when(inventoryMapper.lockStock(2102L, 1)).thenReturn(1);
        when(inventoryMapper.lockStock(2202L, 2)).thenReturn(1);
        doAnswer(invocation -> {
            SalesOrder order = invocation.getArgument(0);
            order.setId(88L);
            return null;
        }).when(orderMapper).insertOrder(any(SalesOrder.class));

        var response = service.createOrder(10L, request);

        ArgumentCaptor<SalesOrder> orderCaptor = ArgumentCaptor.forClass(SalesOrder.class);
        ArgumentCaptor<List<OrderItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).insertOrder(orderCaptor.capture());
        verify(orderMapper).insertItems(itemCaptor.capture());
        verify(cartService).removePurchasedItems(10L, List.of(2102L, 2202L));

        assertThat(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("697.00");
        assertThat(orderCaptor.getValue().getStatus()).isEqualTo("UNPAID");
        assertThat(orderCaptor.getValue().getOrderNo()).startsWith("ORD");
        assertThat(itemCaptor.getValue()).hasSize(2);
        assertThat(itemCaptor.getValue()).extracting(OrderItem::getLineAmount)
                .containsExactly(new BigDecimal("299.00"), new BigDecimal("398.00"));
        assertThat(response.orderNo()).startsWith("ORD");
        assertThat(response.status()).isEqualTo("UNPAID");
        assertThat(response.totalAmount()).isEqualByComparingTo("697.00");
    }

    @Test
    void createOrderRejectsUnsupportedSourceForThisPhase() {
        var request = new CreateOrderRequest("BUY_NOW", List.of(2102L));

        assertThatThrownBy(() -> service.createOrder(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("order source is not supported: BUY_NOW");
    }

    @Test
    void buyNowCreatesUnpaidOrderWithoutTouchingCart() {
        var request = new BuyNowRequest(2102L, 3);
        OrderCheckoutItem checkoutItem = checkoutItem(2102L, "299.00", 0);
        when(orderMapper.findCheckoutItemBySkuId(2102L)).thenReturn(checkoutItem);
        when(inventoryMapper.lockStock(2102L, 3)).thenReturn(1);
        doAnswer(invocation -> {
            SalesOrder order = invocation.getArgument(0);
            order.setId(89L);
            return null;
        }).when(orderMapper).insertOrder(any(SalesOrder.class));

        var response = service.buyNow(10L, request);

        ArgumentCaptor<List<OrderItem>> itemCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderMapper).insertItems(itemCaptor.capture());
        verify(cartService, never()).removePurchasedItems(any(), any());

        assertThat(checkoutItem.getQuantity()).isEqualTo(3);
        assertThat(itemCaptor.getValue())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getOrderId()).isEqualTo(89L);
                    assertThat(item.getSkuId()).isEqualTo(2102L);
                    assertThat(item.getQuantity()).isEqualTo(3);
                    assertThat(item.getLineAmount()).isEqualByComparingTo("897.00");
                });
        assertThat(response.status()).isEqualTo("UNPAID");
        assertThat(response.totalAmount()).isEqualByComparingTo("897.00");
        assertThat(response.items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.skuId()).isEqualTo(2102L);
                    assertThat(item.quantity()).isEqualTo(3);
                    assertThat(item.lineAmount()).isEqualByComparingTo("897.00");
                });
    }

    @Test
    void buyNowRejectsMissingSkuBeforeLockingStock() {
        var request = new BuyNowRequest(999999L, 1);
        when(orderMapper.findCheckoutItemBySkuId(999999L)).thenReturn(null);

        assertThatThrownBy(() -> service.buyNow(10L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("sku not found: 999999");
        verify(inventoryMapper, never()).lockStock(any(), any());
        verify(orderMapper, never()).insertOrder(any(SalesOrder.class));
    }

    @Test
    void createOrderRejectsSkuNotOwnedByCurrentUsersCart() {
        var request = new CreateOrderRequest("CART", List.of(2102L, 2202L));
        when(orderMapper.findCheckoutItemsFromCart(10L, List.of(2102L, 2202L)))
                .thenReturn(List.of(checkoutItem(2102L, "299.00", 1)));

        assertThatThrownBy(() -> service.createOrder(10L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("cart item not found");
    }

    @Test
    void createOrderStopsBeforePersistingWhenStockIsInsufficient() {
        var request = new CreateOrderRequest("CART", List.of(2102L));
        when(orderMapper.findCheckoutItemsFromCart(10L, List.of(2102L)))
                .thenReturn(List.of(checkoutItem(2102L, "299.00", 1)));
        when(inventoryMapper.lockStock(2102L, 1)).thenReturn(0);

        assertThatThrownBy(() -> service.createOrder(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("insufficient stock for sku: 2102");
        verify(orderMapper, never()).insertOrder(any(SalesOrder.class));
        verify(cartService, never()).removePurchasedItems(any(), any());
    }

    @Test
    void cancelUnpaidOrderReleasesLockedStockAndClosesOrder() {
        SalesOrder order = salesOrder(88L, 10L, "ORDCANCEL1", "UNPAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDCANCEL1")).thenReturn(order);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
        when(inventoryMapper.releaseLockedStock(2102L, 2)).thenReturn(1);
        when(orderMapper.updateOrderClosed(88L, "CANCELLED", "用户不想买了")).thenReturn(1);

        var response = service.cancelOrder(10L, "ORDCANCEL1", new CancelOrderRequest("用户不想买了"));

        verify(orderMapper).updateOrderClosed(88L, "CANCELLED", "用户不想买了");
        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.closeReason()).isEqualTo("用户不想买了");
    }

    @Test
    void cancelPaidOrderIsRejectedWithoutReleasingStock() {
        SalesOrder order = salesOrder(88L, 10L, "ORDPAID1", "PAID");
        when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAID1")).thenReturn(order);

        assertThatThrownBy(() -> service.cancelOrder(10L, "ORDPAID1", new CancelOrderRequest("用户不想买了")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("paid order cannot be cancelled in this phase");
        verify(inventoryMapper, never()).releaseLockedStock(any(), any());
        verify(orderMapper, never()).updateOrderClosed(any(), any(), any());
    }

    @Test
    void closeExpiredOrderSkipsPaidOrderWithoutReleasingStock() {
        SalesOrder order = salesOrder(88L, 10L, "ORDPAID1", "PAID");
        when(orderMapper.findOrderByOrderNoForUpdate("ORDPAID1")).thenReturn(order);

        service.closeExpiredOrder("ORDPAID1", "TIMEOUT_UNPAID_30_MINUTES");

        verify(inventoryMapper, never()).releaseLockedStock(any(), any());
        verify(orderMapper, never()).updateOrderClosed(any(), any(), any());
    }

    @Test
    void closeExpiredOrderReleasesLockedStockAndMarksOrderClosed() {
        SalesOrder order = salesOrder(88L, 10L, "ORDTIMEOUT1", "UNPAID");
        when(orderMapper.findOrderByOrderNoForUpdate("ORDTIMEOUT1")).thenReturn(order);
        when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2203L, 1)));
        when(inventoryMapper.releaseLockedStock(2203L, 1)).thenReturn(1);
        when(orderMapper.updateOrderClosed(88L, "CLOSED", "TIMEOUT_UNPAID_30_MINUTES")).thenReturn(1);

        service.closeExpiredOrder("ORDTIMEOUT1", "TIMEOUT_UNPAID_30_MINUTES");

        verify(orderMapper).updateOrderClosed(88L, "CLOSED", "TIMEOUT_UNPAID_30_MINUTES");
        verify(inventoryMapper).releaseLockedStock(eq(2203L), eq(1));
    }

    @Test
    void findExpiredUnpaidOrderNosUsesConfiguredTimeoutAndBatchSize() {
        when(orderMapper.findExpiredUnpaidOrderNos(any(LocalDateTime.class), eq(50)))
                .thenReturn(List.of("ORDTIMEOUT1", "ORDTIMEOUT2"));

        List<String> orderNos = service.findExpiredUnpaidOrderNos(30, 50);

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderMapper).findExpiredUnpaidOrderNos(cutoffCaptor.capture(), eq(50));
        assertThat(orderNos).containsExactly("ORDTIMEOUT1", "ORDTIMEOUT2");
        assertThat(cutoffCaptor.getValue()).isBefore(LocalDateTime.now().minusMinutes(29));
        assertThat(cutoffCaptor.getValue()).isAfter(LocalDateTime.now().minusMinutes(31));
    }

    private OrderCheckoutItem checkoutItem(Long skuId, String salePrice, int quantity) {
        OrderCheckoutItem item = new OrderCheckoutItem();
        item.setSkuId(skuId);
        item.setSpuId(skuId == 2102L ? 1002L : 1003L);
        item.setSkuCode(skuId == 2102L ? "JK-COMMUTE-001-BLK-L" : "PANTS-STRAIGHT-001-BLK-L");
        item.setSpuCode(skuId == 2102L ? "JACKET_COMMUTE_001" : "PANTS_STRAIGHT_001");
        item.setProductName(skuId == 2102L ? "commute jacket" : "straight pants");
        item.setCategoryName(skuId == 2102L ? "jacket" : "pants");
        item.setColor("black");
        item.setSize("L");
        item.setSalePrice(new BigDecimal(salePrice));
        item.setQuantity(quantity);
        item.setMainImageUrl("/images/products/item.jpg");
        item.setSkuStatus("on_sale");
        item.setSpuStatus("on_sale");
        item.setAvailableStock(10);
        return item;
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
}
