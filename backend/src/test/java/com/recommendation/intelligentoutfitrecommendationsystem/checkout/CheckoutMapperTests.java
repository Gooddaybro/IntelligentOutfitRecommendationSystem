package com.recommendation.intelligentoutfitrecommendationsystem.checkout;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.mapper.CheckoutMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutFactRow;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class CheckoutMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8700);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private CheckoutMapper checkoutMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void readsOwnedCartQuantityAndCurrentTradeFactsForSelectedSkus() {
        Long ownerId = createUser();
        Long otherId = createUser();
        cartMapper.upsertItem(ownerId, 2102L, 2);
        cartMapper.upsertItem(ownerId, 2202L, 1);
        cartMapper.upsertItem(otherId, 2102L, 9);
        jdbcTemplate.update("UPDATE product_sku SET sale_price = 123.45, status = 'off_sale' WHERE id = 2102");
        jdbcTemplate.update("UPDATE inventory SET available_stock = 1 WHERE sku_id = 2102");

        List<CheckoutFactRow> rows = checkoutMapper.findCartFacts(ownerId, List.of(2102L));

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.getSkuId()).isEqualTo(2102L);
            assertThat(row.getQuantity()).isEqualTo(2);
            assertThat(row.getSalePrice()).isEqualByComparingTo("123.45");
            assertThat(row.getSkuStatus()).isEqualTo("off_sale");
            assertThat(row.getAvailableStock()).isOne();
        });
    }

    @Test
    void returnsOnlyExistingSelectedRowsFromCurrentUsersCart() {
        Long userId = createUser();
        cartMapper.upsertItem(userId, 2102L, 1);
        cartMapper.upsertItem(userId, 2202L, 2);

        assertThat(checkoutMapper.findCartFacts(userId, List.of(2202L, 999999L)))
                .extracting(CheckoutFactRow::getSkuId)
                .containsExactly(2202L);
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("checkout_mapper_user_" + USER_SEQUENCE.incrementAndGet());
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }
}
