package com.recommendation.intelligentoutfitrecommendationsystem.aftersale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AfterSaleControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(13000);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsAfterSaleCreateWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/after-sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "ORD1",
                                  "type": "REFUND",
                                  "reason": "尺码不合适"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsAndCancelsCurrentUsersAfterSaleRequestForPaidOrder() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        resetInventory(2005, 5);
        addCartItem(accessToken, 2005, 1);
        String orderNo = createOrder(accessToken, 2005);
        mockPay(accessToken, orderNo);

        String createBody = mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s",
                                  "type": "REFUND",
                                  "reason": "尺码不合适"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestNo").isNotEmpty())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.refundAmount").value(99.0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String requestNo = objectMapper.readTree(createBody).path("data").path("requestNo").asText();

        mockMvc.perform(get("/api/after-sales")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestNo").value(requestNo))
                .andExpect(jsonPath("$.data[0].status").value("REQUESTED"));

        mockMvc.perform(post("/api/after-sales/{requestNo}/cancel", requestNo)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "用户撤销"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestNo").value(requestNo))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.handlerNote").value("用户撤销"));
    }

    @Test
    void rejectsAfterSaleForUnpaidOrder() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        resetInventory(2005, 5);
        addCartItem(accessToken, 2005, 1);
        String orderNo = createOrder(accessToken, 2005);

        mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s",
                                  "type": "REFUND",
                                  "reason": "还没支付"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
    }

    @Test
    void doesNotExposeAnotherUsersAfterSaleRequests() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());
        resetInventory(2005, 5);
        addCartItem(ownerToken, 2005, 1);
        String orderNo = createOrder(ownerToken, 2005);
        mockPay(ownerToken, orderNo);

        String createBody = mockMvc.perform(post("/api/after-sales")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s",
                                  "type": "REFUND",
                                  "reason": "尺码不合适"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String requestNo = objectMapper.readTree(createBody).path("data").path("requestNo").asText();

        mockMvc.perform(get("/api/after-sales")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/after-sales/{requestNo}/cancel", requestNo)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"));
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

    private String createOrder(String accessToken, long skuId) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [%d]
                                }
                                """.formatted(skuId)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("orderNo").asText();
    }

    private void mockPay(String accessToken, String orderNo) throws Exception {
        mockMvc.perform(post("/api/payments/mock-pay")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isOk());
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
        return "after_sale_user_" + USER_SEQUENCE.incrementAndGet();
    }

    private void resetInventory(long skuId, int availableStock) {
        jdbcTemplate.update("""
                UPDATE inventory
                SET available_stock = ?,
                    locked_stock = 0,
                    sold_stock = 0
                WHERE sku_id = ?
                """, availableStock, skuId);
    }
}
