package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderAddressSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.support.BaseMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class TrustedCheckoutMySqlIntegrationTests extends BaseMySqlContainerTest {

    private static final Long SKU_ID = 11012L;

    private static final int QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @MockitoSpyBean
    private OrderMapper orderMapper;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void snapshotFailureAfterStockLockRollsBackEntireCheckoutTransaction() {
        Long userId = createUser();
        Long addressId = createAddress(userId);
        cartMapper.upsertItem(userId, SKU_ID, QUANTITY);
        String idempotencyKey = UUID.randomUUID().toString();
        InventoryState inventoryBefore = inventoryState();
        int ordersBefore = count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId);
        int itemsBefore = countOrderChildren("order_item", userId);
        int snapshotsBefore = countOrderChildren("order_address_snapshot", userId);
        int eventsBefore = count(
                "SELECT COUNT(*) FROM behavior_event WHERE user_id = ? AND event_type = 'ORDER_CREATED'",
                userId
        );
        AtomicBoolean lockedStateObserved = new AtomicBoolean();
        doAnswer(invocation -> {
            assertThat(inventoryState()).isEqualTo(new InventoryState(
                    inventoryBefore.availableStock() - QUANTITY,
                    inventoryBefore.lockedStock() + QUANTITY
            ));
            assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId))
                    .isEqualTo(ordersBefore + 1);
            assertThat(countOrderChildren("order_item", userId)).isEqualTo(itemsBefore + 1);
            assertThat(count(
                    "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                    userId,
                    idempotencyKey
            )).isOne();
            lockedStateObserved.set(true);
            throw new IllegalStateException("injected snapshot failure");
        }).when(orderMapper).insertAddressSnapshot(anyLong(), any(OrderAddressSnapshot.class));

        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                idempotencyKey,
                new CreateOrderRequest("CART", List.of(SKU_ID), addressId)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected snapshot failure");

        assertThat(lockedStateObserved).isTrue();
        assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId)).isEqualTo(ordersBefore);
        assertThat(countOrderChildren("order_item", userId)).isEqualTo(itemsBefore);
        assertThat(countOrderChildren("order_address_snapshot", userId)).isEqualTo(snapshotsBefore);
        assertThat(inventoryState()).isEqualTo(inventoryBefore);
        assertThat(count("SELECT COUNT(*) FROM cart_item WHERE user_id = ? AND sku_id = ?", userId, SKU_ID))
                .isOne();
        assertThat(count(
                "SELECT COUNT(*) FROM behavior_event WHERE user_id = ? AND event_type = 'ORDER_CREATED'",
                userId
        )).isEqualTo(eventsBefore);
        assertThat(count(
                "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                userId,
                idempotencyKey
        )).isZero();
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("trusted_checkout_" + UUID.randomUUID().toString().replace("-", ""));
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }

    private Long createAddress(Long userId) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setRecipientName("事务测试收件人");
        address.setPhone("13800138000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("西湖区");
        address.setDetail("文一路 88 号");
        address.setIsDefault(true);
        addressMapper.insert(address);
        return address.getId();
    }

    private InventoryState inventoryState() {
        return jdbcTemplate.queryForObject(
                "SELECT available_stock, locked_stock FROM inventory WHERE sku_id = ?",
                (resultSet, rowNumber) -> new InventoryState(
                        resultSet.getInt("available_stock"),
                        resultSet.getInt("locked_stock")
                ),
                SKU_ID
        );
    }

    private int countOrderChildren(String tableName, Long userId) {
        return count(
                "SELECT COUNT(*) FROM " + tableName
                        + " child JOIN sales_order so ON so.id = child.order_id WHERE so.user_id = ?",
                userId
        );
    }

    private int count(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arguments);
    }

    private record InventoryState(int availableStock, int lockedStock) {
    }
}
