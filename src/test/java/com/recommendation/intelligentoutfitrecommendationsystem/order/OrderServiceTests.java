package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}
