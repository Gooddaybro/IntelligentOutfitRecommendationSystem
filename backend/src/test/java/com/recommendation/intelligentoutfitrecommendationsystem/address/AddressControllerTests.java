package com.recommendation.intelligentoutfitrecommendationsystem.address;

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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AddressControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8300);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiresAuthenticationForAddressBook() throws Exception {
        mockMvc.perform(get("/api/addresses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsUpdatesSwitchesAndDeletesCurrentUsersAddresses() throws Exception {
        String token = registerAndLogin(nextUsername());

        long firstId = createAddress(token, "张三", "13800138000", "文一路 1 号", true);
        long secondId = createAddress(token, "李四", "13900139000", "文二路 2 号", false);

        mockMvc.perform(put("/api/addresses/{addressId}/default", secondId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(secondId))
                .andExpect(jsonPath("$.data[0].isDefault").value(true))
                .andExpect(jsonPath("$.data[1].isDefault").value(false));

        mockMvc.perform(put("/api/addresses/{addressId}", firstId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("王五", "13700137000", "更新后的地址")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %d)].recipientName".formatted(firstId)).value("王五"));

        mockMvc.perform(delete("/api/addresses/{addressId}", secondId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(firstId))
                .andExpect(jsonPath("$.data[0].isDefault").value(true));
    }

    @Test
    void foreignAndRandomAddressIdsReturnSameNotFoundResponse() throws Exception {
        String ownerToken = registerAndLogin(nextUsername());
        String otherToken = registerAndLogin(nextUsername());
        long ownerAddressId = createAddress(ownerToken, "归属用户", "13600136000", "归属地址", true);

        assertNotFoundForUpdate(otherToken, ownerAddressId);
        assertNotFoundForUpdate(otherToken, 99999999L);

        mockMvc.perform(delete("/api/addresses/{addressId}", ownerAddressId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("address not found"));
        assertNotFoundForDelete(otherToken, 99999999L);

        mockMvc.perform(put("/api/addresses/{addressId}/default", ownerAddressId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("address not found"));
        assertNotFoundForDefault(otherToken, 99999999L);

        mockMvc.perform(get("/api/addresses")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void validatesAddressFieldsBeforeWriting() throws Exception {
        String token = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("", "not-a-phone", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("validation_failed"));
    }

    private long createAddress(String token, String name, String phone, String detail, boolean expectedDefault)
            throws Exception {
        String body = mockMvc.perform(post("/api/addresses")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson(name, phone, detail)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode items = objectMapper.readTree(body).path("data");
        JsonNode created = null;
        for (JsonNode item : items) {
            if (name.equals(item.path("recipientName").asText())) {
                created = item;
            }
        }
        if (created == null || created.path("isDefault").asBoolean() != expectedDefault) {
            throw new AssertionError("created address default state did not match expectation");
        }
        return created.path("id").asLong();
    }

    private void assertNotFoundForUpdate(String token, long addressId) throws Exception {
        mockMvc.perform(put("/api/addresses/{addressId}", addressId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(addressJson("攻击者", "13500135000", "猜测地址")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("address not found"));
    }

    private void assertNotFoundForDelete(String token, long addressId) throws Exception {
        mockMvc.perform(delete("/api/addresses/{addressId}", addressId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("address not found"));
    }

    private void assertNotFoundForDefault(String token, long addressId) throws Exception {
        mockMvc.perform(put("/api/addresses/{addressId}/default", addressId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("address not found"));
    }

    private String addressJson(String name, String phone, String detail) {
        return """
                {
                  "recipientName": "%s",
                  "phone": "%s",
                  "province": "浙江省",
                  "city": "杭州市",
                  "district": "西湖区",
                  "detail": "%s"
                }
                """.formatted(name, phone, detail);
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
        return objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String nextUsername() {
        return "address_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
