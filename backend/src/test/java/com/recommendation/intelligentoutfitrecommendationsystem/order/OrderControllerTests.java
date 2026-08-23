package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderAddressSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(9000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsOrderListWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsOrderFromCurrentUsersCartThenListsAndReadsDetail() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        addCartItem(accessToken, 2103, 1);
        addCartItem(accessToken, 2203, 2);
        long addressId = createAddress(accessToken);

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2103, 2203],
                                  "addressId": %d,
                                  "totalAmount": 0.01
                                }
                                """.formatted(addressId)))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.totalAmount").value(697.0))
                .andExpect(jsonPath("$.data.items[0].skuId").value(2103))
                .andExpect(jsonPath("$.data.items[0].salePrice").value(299.0))
                .andExpect(jsonPath("$.data.items[1].skuId").value(2203))
                .andExpect(jsonPath("$.data.items[1].quantity").value(2))
                .andExpect(jsonPath("$.data.items[1].lineAmount").value(398.0))
                .andExpect(jsonPath("$.data.closedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.closeReason").value(nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createBody).path("data").path("orderNo").asText();

        mockMvc.perform(get("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].skuId", not(hasItem(2103))))
                .andExpect(jsonPath("$.data[*].skuId", not(hasItem(2203))));

        mockMvc.perform(get("/api/orders")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].orderNo", hasItem(orderNo)));

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.items[0].skuId").value(2103))
                .andExpect(jsonPath("$.data.items[1].skuId").value(2203));
    }

    @Test
    void rejectsBuyNowSourceUntilNextOrderPhase() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "BUY_NOW",
                                  "skuIds": [2101],
                                  "addressId": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
    }

    @Test
    void buyNowCreatesOrderWithoutChangingCart() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        addCartItem(accessToken, 2203, 1);

        String createBody = mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2103,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("UNPAID"))
                .andExpect(jsonPath("$.data.totalAmount").value(598.0))
                .andExpect(jsonPath("$.data.items[0].skuId").value(2103))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(598.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createBody).path("data").path("orderNo").asText();

        mockMvc.perform(get("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].skuId", hasItem(2203)))
                .andExpect(jsonPath("$.data[*].skuId", not(hasItem(2103))));

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].skuId").value(2103));
    }

    @Test
    void rejectsInvalidBuyNowQuantity() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2103,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
    }

    @Test
    void rejectsBuyNowWithoutIdempotencyKey() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2103,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
    }

    @Test
    void rejectsCartCheckoutWithMissingOrInvalidIdempotencyKey() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        String body = """
                {
                  "source": "CART",
                  "skuIds": [2103],
                  "addressId": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", "fixed-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
    }

    @Test
    void replaysBuyNowAndRejectsSameKeyWithDifferentQuantity() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        String key = UUID.randomUUID().toString();
        Integer lockedStockBefore = jdbcTemplate.queryForObject(
                "SELECT locked_stock FROM inventory WHERE sku_id = 2103",
                Integer.class
        );
        String body = """
                {
                  "skuId": 2103,
                  "quantity": 1
                }
                """;

        String firstBody = mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "false"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String orderNo = objectMapper.readTree(firstBody).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string("Idempotency-Replayed", "true"))
                .andExpect(jsonPath("$.data.orderNo").value(orderNo));

        mockMvc.perform(post("/api/orders/buy-now")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2103,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("idempotency_key_reused"));

        Integer lockedStockAfter = jdbcTemplate.queryForObject(
                "SELECT locked_stock FROM inventory WHERE sku_id = 2103",
                Integer.class
        );
        Integer orderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order WHERE order_no = ?",
                Integer.class,
                orderNo
        );
        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM behavior_event WHERE order_no = ? AND event_type = 'ORDER_CREATED'",
                Integer.class,
                orderNo
        );
        Integer idempotencyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_idempotency WHERE idempotency_key = ?",
                Integer.class,
                key
        );
        assertThat(lockedStockAfter).isEqualTo(lockedStockBefore + 1);
        assertThat(orderCount).isOne();
        assertThat(eventCount).isOne();
        assertThat(idempotencyCount).isOne();
    }

    @Test
    void failedBuyNowRollsBackClaimAndAllowsSameKeyToRetry() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        String key = UUID.randomUUID().toString();
        Integer availableBefore = jdbcTemplate.queryForObject(
                "SELECT available_stock FROM inventory WHERE sku_id = 2005",
                Integer.class
        );
        Integer lockedBefore = jdbcTemplate.queryForObject(
                "SELECT locked_stock FROM inventory WHERE sku_id = 2005",
                Integer.class
        );
        jdbcTemplate.update("UPDATE inventory SET available_stock = 0 WHERE sku_id = 2005");

        try {
            String body = """
                    {
                      "skuId": 2005,
                      "quantity": 1
                    }
                    """;
            mockMvc.perform(post("/api/orders/buy-now")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("bad_request"));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_idempotency WHERE idempotency_key = ?",
                    Integer.class,
                    key
            )).isZero();

            jdbcTemplate.update(
                    "UPDATE inventory SET available_stock = ?, locked_stock = ? WHERE sku_id = 2005",
                    availableBefore,
                    lockedBefore
            );
            mockMvc.perform(post("/api/orders/buy-now")
                            .header("Authorization", "Bearer " + accessToken)
                            .header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Idempotency-Replayed", "false"));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM order_idempotency WHERE idempotency_key = ?",
                    Integer.class,
                    key
            )).isOne();
        } finally {
            jdbcTemplate.update(
                    "UPDATE inventory SET available_stock = ?, locked_stock = ? WHERE sku_id = 2005",
                    availableBefore,
                    lockedBefore
            );
        }
    }

    @Test
    void keepsOrdersScopedToTheOwnerAccessToken() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());

        addCartItem(ownerToken, 2005, 1);
        long addressId = createAddress(ownerToken);

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2005],
                                  "addressId": %d
                                }
                                """.formatted(addressId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createBody).path("data").path("orderNo").asText();

        mockMvc.perform(get("/api/orders/{orderNo}", orderNo)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"));
    }

    @Test
    void cancelsCurrentUsersUnpaidOrderAndKeepsCancelScopedToOwner() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());

        addCartItem(ownerToken, 2005, 1);
        long addressId = createAddress(ownerToken);

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2005],
                                  "addressId": %d
                                }
                                """.formatted(addressId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String orderNo = objectMapper.readTree(createBody).path("data").path("orderNo").asText();

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用户不想买了\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"));

        mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"用户不想买了\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.closedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.closeReason").value("用户不想买了"));
    }

    @Test
    void rejectsEmptyCheckoutSkuListBeforeTouchingOrderService() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [],
                                  "addressId": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
    }

    @Test
    void rejectsCartCheckoutWithoutAddressId() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        addCartItem(accessToken, 2103, 1);

        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2103]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
    }

    @Test
    void orderResponseExposesTheHistoricalAddressSnapshot() {
        OrderAddressSnapshot snapshot = new OrderAddressSnapshot(
                7L,
                "林木",
                "13800000000",
                "浙江省",
                "杭州市",
                "西湖区",
                "文一路 88 号"
        );
        OrderResponse response = new OrderResponse(
                "ORD-ADDRESS",
                "UNPAID",
                new BigDecimal("299.00"),
                List.of(),
                snapshot,
                null,
                null,
                null,
                null
        );

        JsonNode address = objectMapper.valueToTree(response).path("address");

        assertThat(address.path("id").asLong()).isEqualTo(7L);
        assertThat(address.path("recipientName").asText()).isEqualTo("林木");
        assertThat(address.path("phone").asText()).isEqualTo("13800000000");
        assertThat(address.path("province").asText()).isEqualTo("浙江省");
        assertThat(address.path("city").asText()).isEqualTo("杭州市");
        assertThat(address.path("district").asText()).isEqualTo("西湖区");
        assertThat(address.path("detail").asText()).isEqualTo("文一路 88 号");
        assertThat(address.size()).isEqualTo(7);
    }

    private void addCartItem(String accessToken, long skuId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": %d,
                                  "quantity": %d
                                }
                                """.formatted(skuId, quantity)))
                .andExpect(status().isOk());
    }

    private long createAddress(String accessToken) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "订单测试用户",
                                  "phone": "13800138000",
                                  "province": "浙江省",
                                  "city": "杭州市",
                                  "district": "西湖区",
                                  "detail": "文一路 1 号"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").get(0).path("id").asLong();
    }

    private String registerAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "StrongPassword123!",
                                  "email": "%s@example.com"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "StrongPassword123!"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        return data.path("accessToken").asText();
    }

    private String nextUsername() {
        return "order_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
