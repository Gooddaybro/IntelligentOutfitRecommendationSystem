package com.recommendation.intelligentoutfitrecommendationsystem.user;

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

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(2000);

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatesAndReadsCurrentUserProfile() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(put("/api/me/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "Alex",
                                  "avatarUrl": "https://example.com/avatar.png",
                                  "gender": "male",
                                  "birthday": "1998-05-20"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Alex"))
                .andExpect(jsonPath("$.data.gender").value("male"));

        mockMvc.perform(get("/api/me/profile")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Alex"))
                .andExpect(jsonPath("$.data.birthday").value("1998-05-20"));
    }

    @Test
    void updatesAndReadsBodyDataForSizeRecommendation() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(put("/api/me/body-data")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "heightCm": 178.5,
                                  "weightKg": 70.2,
                                  "shoulderWidthCm": 45.0,
                                  "bustCm": 96.0,
                                  "waistCm": 80.0,
                                  "hipCm": 95.0,
                                  "preferredFit": "regular"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.heightCm").value(178.5))
                .andExpect(jsonPath("$.data.preferredFit").value("regular"));

        mockMvc.perform(get("/api/me/body-data")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shoulderWidthCm").value(45.0))
                .andExpect(jsonPath("$.data.waistCm").value(80.0));
    }

    @Test
    void updatesAndReadsClothingPreferencesForAiContext() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(put("/api/me/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferredStyles": ["commute", "minimal"],
                                  "preferredColors": ["black", "navy"],
                                  "dislikedColors": ["orange"],
                                  "preferredCategories": ["jacket", "pants"],
                                  "budgetMin": 100,
                                  "budgetMax": 500
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferredStyles", containsInAnyOrder("commute", "minimal")))
                .andExpect(jsonPath("$.data.preferredColors", containsInAnyOrder("black", "navy")))
                .andExpect(jsonPath("$.data.budgetMax").value(500));

        mockMvc.perform(get("/api/me/preferences")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dislikedColors", containsInAnyOrder("orange")))
                .andExpect(jsonPath("$.data.preferredCategories", containsInAnyOrder("jacket", "pants")));
    }

    @Test
    void rejectsInvalidPreferenceBudgetRange() throws Exception {
        String accessToken = registerAndLogin(nextUsername());

        mockMvc.perform(put("/api/me/preferences")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "preferredStyles": ["commute"],
                                  "budgetMin": 900,
                                  "budgetMax": 100
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("bad_request"));
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
        return "profile_user_" + USER_SEQUENCE.incrementAndGet();
    }
}
