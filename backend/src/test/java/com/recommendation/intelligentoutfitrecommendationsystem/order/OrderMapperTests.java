package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.auth.mapper.UserAuthMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.auth.model.UserAccount;
import com.recommendation.intelligentoutfitrecommendationsystem.cart.mapper.CartMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderCheckoutItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderItem;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.SalesOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class OrderMapperTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(9000);

    @Autowired
    private UserAuthMapper userAuthMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertsOrderAndItemsThenReadsOwnedDetail() {
        Long userId = createUser();
        SalesOrder order = new SalesOrder();
        order.setOrderNo("ORDTEST" + userId);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus("UNPAID");

        orderMapper.insertOrder(order);
        orderMapper.insertItems(List.of(orderItem(order.getId(), 2102L)));

        SalesOrder detail = orderMapper.findOrderByUserIdAndOrderNo(userId, order.getOrderNo());

        assertThat(order.getId()).isNotNull();
        assertThat(detail.getOrderNo()).isEqualTo(order.getOrderNo());
        assertThat(orderMapper.findItemsByOrderId(order.getId()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSkuId()).isEqualTo(2102L);
                    assertThat(item.getSalePrice()).isEqualByComparingTo("299.00");
                    assertThat(item.getLineAmount()).isEqualByComparingTo("299.00");
                });
    }

    @Test
    void orderQueriesStayScopedToCurrentUser() {
        Long ownerId = createUser();
        Long otherUserId = createUser();
        SalesOrder order = new SalesOrder();
        order.setOrderNo("ORDPRIVATE" + ownerId);
        order.setUserId(ownerId);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus("UNPAID");
        orderMapper.insertOrder(order);

        assertThat(orderMapper.findOrdersByUserId(ownerId)).extracting(SalesOrder::getOrderNo)
                .contains(order.getOrderNo());
        assertThat(orderMapper.findOrderByUserIdAndOrderNo(otherUserId, order.getOrderNo())).isNull();
    }

    @Test
    void checkoutQueryReturnsSelectedCartItemsWithCurrentProductFacts() {
        Long userId = createUser();
        cartMapper.upsertItem(userId, 2103L, 2);
        cartMapper.upsertItem(userId, 2202L, 1);

        List<OrderCheckoutItem> checkoutItems = orderMapper.findCheckoutItemsFromCart(userId, List.of(2103L));

        assertThat(checkoutItems)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getSkuId()).isEqualTo(2103L);
                    assertThat(item.getQuantity()).isEqualTo(2);
                    assertThat(item.getSpuId()).isEqualTo(1002L);
                    assertThat(item.getSkuCode()).isEqualTo("JK-COMMUTE-001-NAVY-L");
                    assertThat(item.getSpuCode()).isEqualTo("JACKET_COMMUTE_001");
                    assertThat(item.getSalePrice()).isEqualByComparingTo("299.00");
                    assertThat(item.getSkuStatus()).isEqualTo("on_sale");
                    assertThat(item.getSpuStatus()).isEqualTo("on_sale");
                    assertThat(item.getAvailableStock()).isNotNegative();
                });
    }

    @Test
    void checkoutQueryBySkuReturnsCurrentProductFactsWithoutCart() {
        OrderCheckoutItem checkoutItem = orderMapper.findCheckoutItemBySkuId(2103L);

        assertThat(checkoutItem).satisfies(item -> {
            assertThat(item.getSkuId()).isEqualTo(2103L);
            assertThat(item.getQuantity()).isNull();
            assertThat(item.getSpuId()).isEqualTo(1002L);
            assertThat(item.getSkuCode()).isEqualTo("JK-COMMUTE-001-NAVY-L");
            assertThat(item.getSpuCode()).isEqualTo("JACKET_COMMUTE_001");
            assertThat(item.getSalePrice()).isEqualByComparingTo("299.00");
            assertThat(item.getSkuStatus()).isEqualTo("on_sale");
            assertThat(item.getSpuStatus()).isEqualTo("on_sale");
            assertThat(item.getAvailableStock()).isNotNegative();
        });
    }

    @Test
    void checkoutQueryBySkuReturnsNullForMissingSku() {
        assertThat(orderMapper.findCheckoutItemBySkuId(999999L)).isNull();
    }

    @Test
    void closesOrderAndFindsExpiredUnpaidOrders() {
        Long userId = createUser();
        SalesOrder order = new SalesOrder();
        order.setOrderNo("ORDEXPIRED" + userId);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("299.00"));
        order.setStatus("UNPAID");
        orderMapper.insertOrder(order);
        jdbcTemplate.update(
                "UPDATE sales_order SET created_at = DATEADD('MINUTE', -31, CURRENT_TIMESTAMP) WHERE id = ?",
                order.getId()
        );

        List<String> expiredOrderNos = orderMapper.findExpiredUnpaidOrderNos(LocalDateTime.now().minusMinutes(30), 10);
        int updatedRows = orderMapper.updateOrderClosed(order.getId(), "CLOSED", "TIMEOUT_UNPAID_30_MINUTES");
        SalesOrder closedOrder = orderMapper.findOrderByUserIdAndOrderNo(userId, order.getOrderNo());

        assertThat(expiredOrderNos).contains(order.getOrderNo());
        assertThat(updatedRows).isOne();
        assertThat(closedOrder.getStatus()).isEqualTo("CLOSED");
        assertThat(closedOrder.getClosedAt()).isNotNull();
        assertThat(closedOrder.getCloseReason()).isEqualTo("TIMEOUT_UNPAID_30_MINUTES");
    }

    private OrderItem orderItem(Long orderId, Long skuId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setSkuId(skuId);
        item.setSpuId(1002L);
        item.setSkuCode("JK-COMMUTE-001-BLK-L");
        item.setSpuCode("JACKET_COMMUTE_001");
        item.setProductName("commute jacket");
        item.setCategoryName("jacket");
        item.setColor("black");
        item.setSize("L");
        item.setSalePrice(new BigDecimal("299.00"));
        item.setQuantity(1);
        item.setLineAmount(new BigDecimal("299.00"));
        item.setMainImageUrl("/images/products/jacket-commute-main.svg");
        return item;
    }

    private Long createUser() {
        String username = "order_mapper_user_" + USER_SEQUENCE.incrementAndGet();
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
