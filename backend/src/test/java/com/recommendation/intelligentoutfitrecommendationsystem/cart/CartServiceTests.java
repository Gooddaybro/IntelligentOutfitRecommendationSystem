package com.recommendation.intelligentoutfitrecommendationsystem.cart;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItemView;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.service.CartService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTests {

    @Mock
    private CartMapper cartMapper;

    @Mock
    private BehaviorEventService behaviorEventService;

    @InjectMocks
    private CartService service;

    @Test
    void addItemRejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> service.addItem(10L, 2102L, 0))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("quantity must be positive");
    }

    @Test
    void addItemRejectsQuantityAboveCartLimit() {
        assertThatThrownBy(() -> service.addItem(10L, 2102L, 100))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("quantity must not exceed 99");
    }

    @Test
    void addItemRequiresExistingSku() {
        when(cartMapper.existsSkuById(9999L)).thenReturn(0);

        assertThatThrownBy(() -> service.addItem(10L, 9999L, 1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("sku not found: 9999");
    }

    @Test
    void addItemMergesAndReturnsCurrentCart() {
        CartItemView item = cartItemView(2102L, 3);
        when(cartMapper.existsSkuById(2102L)).thenReturn(1);
        when(cartMapper.findItemsByUserId(10L)).thenReturn(List.of(item));

        var items = service.addItem(10L, 2102L, 3);

        assertThat(items).containsExactly(item);
        verify(cartMapper).upsertItem(10L, 2102L, 3);
        verify(behaviorEventService).recordBusinessEvent(argThat(command ->
                "CART_ADD".equals(command.eventType())
                        && Long.valueOf(10L).equals(command.userId())
                        && Long.valueOf(1002L).equals(command.spuId())
                        && Long.valueOf(2102L).equals(command.skuId())
                        && Integer.valueOf(3).equals(command.quantity())
        ));
    }

    @Test
    void addItemPropagatesRecommendationAttribution() {
        CartItemView item = cartItemView(2102L, 1);
        when(cartMapper.existsSkuById(2102L)).thenReturn(1);
        when(cartMapper.findItemsByUserId(10L)).thenReturn(List.of(item));

        service.addItem(10L, 2102L, 1, "rec_cart_test");

        verify(behaviorEventService).recordBusinessEvent(argThat(command ->
                "rec_cart_test".equals(command.recommendationId())
                        && "CART_ADD".equals(command.eventType())
        ));
    }

    @Test
    void updateQuantityConvertsMissingOwnerScopedRowToNotFound() {
        when(cartMapper.updateQuantity(10L, 2102L, 2)).thenReturn(0);

        assertThatThrownBy(() -> service.updateQuantity(10L, 2102L, 2))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("cart item not found");
    }

    @Test
    void deleteItemConvertsMissingOwnerScopedRowToNotFound() {
        when(cartMapper.deleteItem(10L, 2102L)).thenReturn(0);

        assertThatThrownBy(() -> service.deleteItem(10L, 2102L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("cart item not found");
    }

    private CartItemView cartItemView(Long skuId, int quantity) {
        CartItemView item = new CartItemView();
        item.setId(1L);
        item.setUserId(10L);
        item.setSkuId(skuId);
        item.setSpuId(1002L);
        item.setName("commute jacket");
        item.setSalePrice(BigDecimal.valueOf(299));
        item.setStockStatus("in_stock");
        item.setQuantity(quantity);
        return item;
    }
}
