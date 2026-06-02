package com.recommendation.intelligentoutfitrecommendationsystem.cart;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItem;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.model.CartItemView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class CartMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(6000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private CartMapper cartMapper;

    @Test
    void upsertCombinesQuantityForSameUserAndSku() {
        Long userId = createUser();

        cartMapper.upsertItem(userId, 2102L, 1);
        cartMapper.upsertItem(userId, 2102L, 2);

        assertThat(cartMapper.findItemsByUserId(userId))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSkuId()).isEqualTo(2102L);
                    assertThat(item.getQuantity()).isEqualTo(3);
                    assertThat(item.getSpuId()).isEqualTo(1002L);
                    assertThat(item.getSalePrice()).isEqualByComparingTo("299.00");
                    assertThat(item.getStockStatus()).isEqualTo("in_stock");
                });
    }

    @Test
    void updateAndDeleteStayScopedToOwner() {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        cartMapper.upsertItem(ownerId, 2102L, 1);
        cartMapper.upsertItem(otherUserId, 2102L, 4);

        assertThat(cartMapper.updateQuantity(ownerId, 2102L, 3)).isEqualTo(1);
        assertThat(cartMapper.updateQuantity(ownerId, 2202L, 5)).isZero();
        assertThat(cartMapper.deleteItem(ownerId, 2102L)).isEqualTo(1);

        assertThat(cartMapper.findItemsByUserId(ownerId)).isEmpty();
        assertThat(cartMapper.findItemsByUserId(otherUserId))
                .extracting(CartItemView::getQuantity)
                .containsExactly(4);
    }

    @Test
    void insertStoresGeneratedIdentifierForNewItem() {
        Long userId = createUser();
        CartItem item = new CartItem();
        item.setUserId(userId);
        item.setSkuId(2202L);
        item.setQuantity(2);

        cartMapper.insertItem(item);

        assertThat(item.getId()).isNotNull();
        assertThat(cartMapper.findByUserIdAndSkuId(userId, 2202L).getQuantity()).isEqualTo(2);
    }

    private Long createUser() {
        String username = "cart_mapper_user_" + USER_SEQUENCE.incrementAndGet();
        UserAccount userAccount = new UserAccount();
        userAccount.setUsername(username);
        userAccount.setPasswordHash("encoded-password");
        userAccount.setStatus("active");
        userAuthMapper.insertUserAccount(userAccount);

        Long roleId = userAuthMapper.findRoleIdByCode("USER");
        userAuthMapper.insertUserRole(userAccount.getId(), roleId);
        return userAccount.getId();
    }
}
