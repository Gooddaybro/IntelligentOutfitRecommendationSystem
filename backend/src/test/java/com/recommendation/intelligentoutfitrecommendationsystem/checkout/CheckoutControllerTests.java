package com.recommendation.intelligentoutfitrecommendationsystem.checkout;

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
class CheckoutControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8800);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiresAuthenticationForCheckoutPreview() throws Exception {
        mockMvc.perform(post("/api/checkout/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuIds":[2102],"addressId":1}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void calculatesPreviewFromServerFactsAndIgnoresForgedAmountsAndQuantity() throws Exception {
        String token = registerAndLogin(nextUsername());
        long addressId = createAddress(token);
        addCartItem(token, 2102L, 2);

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skuIds": [2102],
                                  "addressId": %d,
                                  "quantity": 999,
                                  "salePrice": 0.01,
                                  "payableAmount": 0.01
                                }
                                """.formatted(addressId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].skuId").value(2102))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.items[0].salePrice").value(299.00))
                .andExpect(jsonPath("$.data.items[0].lineAmount").value(598.00))
                .andExpect(jsonPath("$.data.merchandiseAmount").value(598.00))
                .andExpect(jsonPath("$.data.shippingAmount").value(0.00))
                .andExpect(jsonPath("$.data.discountAmount").value(0.00))
                .andExpect(jsonPath("$.data.payableAmount").value(598.00))
                .andExpect(jsonPath("$.data.invalidReasons").isEmpty());
    }

    @Test
    void rejectsForeignAddressWithSameAddressNotFoundSemantics() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());
        long ownerAddressId = createAddress(ownerToken);

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuIds":[2102],"addressId":%d}
                                """.formatted(ownerAddressId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("address not found"));
    }

    @Test
    void requiresNonEmptySkuSelectionAndAddress() throws Exception {
        String token = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
    }

    @Test
    void mapsUnreadableCheckoutBodiesToBadRequest() throws Exception {
        String token = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("invalid_request"));

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skuIds\":[2102],\"addressId\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("invalid_request"));

        mockMvc.perform(post("/api/checkout/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuIds":[2102],"addressId":"not-a-number"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("invalid_request"));
    }

    private long createAddress(String token) throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "recipientName": "结算用户",
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

    private void addCartItem(String token, Long skuId, int quantity) throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"skuId":%d,"quantity":%d}
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
                                {"username":"%s","password":"StrongPassword123!"}
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        return data.path("accessToken").asText();
    }

    private String nextUsername() {
        return "checkout_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
