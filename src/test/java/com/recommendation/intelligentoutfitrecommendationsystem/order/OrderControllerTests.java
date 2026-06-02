package com.recommendation.intelligentoutfitrecommendationsystem.order;

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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(9000);

    @Autowired
    private MockMvc mockMvc;

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

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2103, 2203]
                                }
                                """))
                .andExpect(status().isOk())
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "BUY_NOW",
                                  "skuIds": [2101]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
    }

    @Test
    void keepsOrdersScopedToTheOwnerAccessToken() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());

        addCartItem(ownerToken, 2005, 1);

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2005]
                                }
                                """))
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

        String createBody = mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": [2005]
                                }
                                """))
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "CART",
                                  "skuIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
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
