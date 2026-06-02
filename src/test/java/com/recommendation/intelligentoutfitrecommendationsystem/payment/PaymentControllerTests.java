package com.recommendation.intelligentoutfitrecommendationsystem.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(12000);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsMockPayWithoutAccessToken() throws Exception {
        mockMvc.perform(post("/api/payments/mock-pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderNo\":\"ORD123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void mockPayCurrentUsersOrderAndReturnsExistingPaymentOnRepeat() throws Exception {
        String accessToken = registerAndLogin(nextUsername());
        addCartItem(accessToken, 2005, 1);
        String orderNo = createOrder(accessToken, 2005);

        String firstPayBody = mockMvc.perform(post("/api/payments/mock-pay")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").isNotEmpty())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.amount").value(99.0))
                .andExpect(jsonPath("$.data.channel").value("MOCK"))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.data.paidAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String paymentNo = objectMapper.readTree(firstPayBody).path("data").path("paymentNo").asText();

        mockMvc.perform(post("/api/payments/mock-pay")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s"
                                }
                                """.formatted(orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentNo").value(paymentNo))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    void mockPayRejectsOrderOwnedByAnotherUser() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());
        addCartItem(ownerToken, 2005, 1);
        String orderNo = createOrder(ownerToken, 2005);

        mockMvc.perform(post("/api/payments/mock-pay")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "%s"
                                }
                                """.formatted(orderNo)))
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
        return "payment_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
