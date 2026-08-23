package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.IdempotencyKeyConflictException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.CreateOrderRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.IdempotentOrderResult;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderService;
import com.recommendation.intelligentoutfitrecommendationsystem.support.BaseMySqlContainerTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class OrderIdempotencyMySqlConcurrencyTests extends BaseMySqlContainerTest {

    private static final Long SKU_ID = 11012L;

    private static final int QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentDuplicateCartCheckoutCreatesOneCompleteOrderAndChangedAddressConflicts() throws Exception {
        Long userId = createUser();
        Long addressId = createAddress(userId, "文一路 88 号", true);
        Long otherAddressId = createAddress(userId, "余杭塘路 99 号", false);
        cartMapper.upsertItem(userId, SKU_ID, QUANTITY);
        String key = UUID.randomUUID().toString();
        InventoryState inventoryBefore = inventoryState();
        int ordersBefore = count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId);
        int snapshotsBefore = countOrderChildren("order_address_snapshot", userId);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<IdempotentOrderResult> first = executor.submit(
                    () -> submit(userId, addressId, key, ready, start)
            );
            Future<IdempotentOrderResult> second = executor.submit(
                    () -> submit(userId, addressId, key, ready, start)
            );
            ready.await();
            start.countDown();

            List<IdempotentOrderResult> results = List.of(first.get(), second.get());

            assertThat(results).extracting(result -> result.order().orderNo())
                    .containsOnly(results.getFirst().order().orderNo());
            assertThat(results).extracting(IdempotentOrderResult::replayed)
                    .containsExactlyInAnyOrder(false, true);
            assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId))
                    .isEqualTo(ordersBefore + 1);
            assertThat(countOrderChildren("order_address_snapshot", userId))
                    .isEqualTo(snapshotsBefore + 1);
            assertThat(inventoryState()).isEqualTo(new InventoryState(
                    inventoryBefore.availableStock() - QUANTITY,
                    inventoryBefore.lockedStock() + QUANTITY
            ));
            assertThat(count(
                    "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                    userId,
                    key
            )).isOne();

            assertThatThrownBy(() -> orderService.createOrder(
                    userId,
                    key,
                    new CreateOrderRequest("CART", List.of(SKU_ID), otherAddressId)
            ))
                    .isInstanceOf(IdempotencyKeyConflictException.class)
                    .hasMessage("Idempotency-Key was already used with different request parameters");

            assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId))
                    .isEqualTo(ordersBefore + 1);
            assertThat(countOrderChildren("order_address_snapshot", userId))
                    .isEqualTo(snapshotsBefore + 1);
            assertThat(inventoryState()).isEqualTo(new InventoryState(
                    inventoryBefore.availableStock() - QUANTITY,
                    inventoryBefore.lockedStock() + QUANTITY
            ));
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private IdempotentOrderResult submit(
            Long userId,
            Long addressId,
            String key,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return orderService.createOrder(
                userId,
                key,
                new CreateOrderRequest("CART", List.of(SKU_ID), addressId)
        );
    }

    private Long createUser() {
        UserAccount account = new UserAccount();
        account.setUsername("idempotency_" + UUID.randomUUID().toString().replace("-", ""));
        account.setPasswordHash("encoded-password");
        account.setStatus("active");
        userAuthMapper.insertUserAccount(account);
        userAuthMapper.insertUserRole(account.getId(), userAuthMapper.findRoleIdByCode("USER"));
        return account.getId();
    }

    private Long createAddress(Long userId, String detail, boolean isDefault) {
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setRecipientName("幂等测试收件人");
        address.setPhone("13800138000");
        address.setProvince("浙江省");
        address.setCity("杭州市");
        address.setDistrict("西湖区");
        address.setDetail(detail);
        address.setIsDefault(isDefault);
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
