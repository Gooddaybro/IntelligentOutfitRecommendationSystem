package com.recommendation.intelligentoutfitrecommendationsystem.cart;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8000);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsCartListWithoutAccessToken() throws Exception {
        mockMvc.perform(get("/api/cart/items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addsUpdatesDeletesAndClearsCurrentUsersCart() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2102,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].skuId").value(2102))
                .andExpect(jsonPath("$.data[0].quantity").value(1))
                .andExpect(jsonPath("$.data[0].spuCode").value("JACKET_COMMUTE_001"));

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2102,
                                  "quantity": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(3));

        mockMvc.perform(put("/api/cart/items/{skuId}", 2102)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "quantity": 5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantity").value(5));

        mockMvc.perform(delete("/api/cart/items/{skuId}", 2102)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2202,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void cartItemsAreIsolatedByAccessTokenUser() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2102,
                                  "quantity": 1
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cart/items")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].skuId", not(hasItem(2102))));
    }

    @Test
    void rejectsInvalidQuantityBeforeWritingCart() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuId": 2102,
                                  "quantity": 0
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
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
        return "cart_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
