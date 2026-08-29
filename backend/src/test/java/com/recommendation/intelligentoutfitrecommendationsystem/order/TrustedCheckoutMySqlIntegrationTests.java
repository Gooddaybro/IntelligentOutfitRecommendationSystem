package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.address.mapper.AddressMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.address.model.UserAddress;
import com.recommendation.intelligentoutfitrecommendationsystem.address.service.AddressService;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.mapper.CheckoutMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.checkout.model.CheckoutFactRow;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

@EnabledIfEnvironmentVariable(named = "RUN_MYSQL_TESTS", matches = "true")
class TrustedCheckoutMySqlIntegrationTests extends BaseMySqlContainerTest {

    private static final Long SKU_ID = 11012L;

    private static final Long OTHER_SKU_ID = 11022L;

    private static final int QUANTITY = 2;

    @Autowired
    private OrderService orderService;

    @MockitoSpyBean
    private AddressService addressService;

    @MockitoSpyBean
    private OrderMapper orderMapper;

    @MockitoSpyBean
    private BehaviorMapper behaviorMapper;

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private AddressMapper addressMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private CheckoutMapper checkoutMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

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

    @Test
    void eventFailureAfterCompleteOrderPersistenceRollsBackEntireCheckoutTransaction() {
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
        AtomicBoolean completeOrderObserved = new AtomicBoolean();
        doAnswer(invocation -> {
            BehaviorEvent event = invocation.getArgument(0);
            if (!"ORDER_CREATED".equals(event.getEventType()) || !userId.equals(event.getUserId())) {
                return invocation.callRealMethod();
            }
            assertThat(inventoryState()).isEqualTo(new InventoryState(
                    inventoryBefore.availableStock() - QUANTITY,
                    inventoryBefore.lockedStock() + QUANTITY
            ));
            assertThat(count("SELECT COUNT(*) FROM sales_order WHERE user_id = ?", userId))
                    .isEqualTo(ordersBefore + 1);
            assertThat(countOrderChildren("order_item", userId)).isEqualTo(itemsBefore + 1);
            assertThat(countOrderChildren("order_address_snapshot", userId)).isEqualTo(snapshotsBefore + 1);
            assertThat(count(
                    "SELECT COUNT(*) FROM order_idempotency WHERE user_id = ? AND idempotency_key = ?",
                    userId,
                    idempotencyKey
            )).isOne();
            completeOrderObserved.set(true);
            throw new IllegalStateException("injected order event failure");
        }).when(behaviorMapper).insert(any(BehaviorEvent.class));

        assertThatThrownBy(() -> orderService.createOrder(
                userId,
                idempotencyKey,
                new CreateOrderRequest("CART", List.of(SKU_ID), addressId)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("injected order event failure");

        assertThat(completeOrderObserved).isTrue();
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

    @Test
    void committedQuantityUpdateAfterAddressReadIsUsedByFormalCheckout() throws Exception {
        Long userId = createUser();
        Long addressId = createAddress(userId);
        cartMapper.upsertItem(userId, SKU_ID, QUANTITY);
        CountDownLatch addressRead = new CountDownLatch(1);
        CountDownLatch releaseCheckout = new CountDownLatch(1);
        AtomicBoolean firstAddressRead = new AtomicBoolean();
        doAnswer(invocation -> {
            Object address = invocation.callRealMethod();
            if (firstAddressRead.compareAndSet(false, true)) {
                addressRead.countDown();
                assertThat(releaseCheckout.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return address;
        }).when(addressService).requireOwnedAddress(userId, addressId);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> checkout = executor.submit(() -> orderService.createOrder(
                    userId,
                    UUID.randomUUID().toString(),
                    new CreateOrderRequest("CART", List.of(SKU_ID), addressId)
            ));
            if (!addressRead.await(10, TimeUnit.SECONDS)) {
                checkout.get(1, TimeUnit.SECONDS);
                throw new AssertionError("checkout did not reach the coordinated address read");
            }
            Future<Integer> update = executor.submit(() -> transactionTemplate.execute(
                    status -> cartMapper.updateQuantity(userId, SKU_ID, 3)
            ));
            assertThat(update.get(10, TimeUnit.SECONDS)).isOne();

            releaseCheckout.countDown();
            checkout.get(10, TimeUnit.SECONDS);

            assertThat(count("SELECT COUNT(*) FROM cart_item WHERE user_id = ? AND sku_id = ?", userId, SKU_ID))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT oi.quantity FROM order_item oi "
                            + "JOIN sales_order so ON so.id = oi.order_id "
                            + "WHERE so.user_id = ? AND oi.sku_id = ? ORDER BY oi.id DESC LIMIT 1",
                    Integer.class,
                    userId,
                    SKU_ID
            )).isEqualTo(3);
        } finally {
            releaseCheckout.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void lockingFactsForDifferentSkusSharingDimensionsDoNotBlockEachOther() throws Exception {
        Long firstUserId = createUser();
        Long secondUserId = createUser();
        cartMapper.upsertItem(firstUserId, SKU_ID, QUANTITY);
        cartMapper.upsertItem(secondUserId, OTHER_SKU_ID, QUANTITY);
        assertThat(count("""
                SELECT COUNT(*)
                FROM product_sku first_sku
                JOIN product_sku second_sku
                  ON second_sku.id = ?
                 AND second_sku.spu_id <> first_sku.spu_id
                 AND second_sku.color_id = first_sku.color_id
                 AND second_sku.size_id = first_sku.size_id
                JOIN product_spu first_spu ON first_spu.id = first_sku.spu_id
                JOIN product_spu second_spu
                  ON second_spu.id = second_sku.spu_id
                 AND second_spu.category_id = first_spu.category_id
                WHERE first_sku.id = ?
                """, OTHER_SKU_ID, SKU_ID)).isOne();
        CountDownLatch firstFactsLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstRead = new CountDownLatch(1);
        CountDownLatch secondFactsRead = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> firstRead = executor.submit(() -> transactionTemplate.execute(status -> {
            List<CheckoutFactRow> facts = checkoutMapper.findCartFactsForUpdate(firstUserId, List.of(SKU_ID));
            assertThat(facts).extracting(CheckoutFactRow::getSkuId).containsExactly(SKU_ID);
            firstFactsLocked.countDown();
            await(releaseFirstRead, "first locking fact read was not released");
            return null;
        }));
        Future<?> secondRead = null;

        try {
            assertThat(firstFactsLocked.await(10, TimeUnit.SECONDS)).isTrue();
            secondRead = executor.submit(() -> transactionTemplate.execute(status -> {
                List<CheckoutFactRow> facts = checkoutMapper.findCartFactsForUpdate(
                        secondUserId,
                        List.of(OTHER_SKU_ID)
                );
                assertThat(facts).extracting(CheckoutFactRow::getSkuId).containsExactly(OTHER_SKU_ID);
                secondFactsRead.countDown();
                return null;
            }));

            assertThat(secondFactsRead.await(3, TimeUnit.SECONDS))
                    .as("unrelated cart facts must not wait on shared dimension rows")
                    .isTrue();
            secondRead.get(1, TimeUnit.SECONDS);
        } finally {
            releaseFirstRead.countDown();
            firstRead.get(10, TimeUnit.SECONDS);
            if (secondRead != null) {
                secondRead.get(10, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
        }
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

    private void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failureMessage, exception);
        }
    }

    private record InventoryState(int availableStock, int lockedStock) {
    }
}
