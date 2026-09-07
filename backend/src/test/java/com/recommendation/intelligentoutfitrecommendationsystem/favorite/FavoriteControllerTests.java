package com.recommendation.intelligentoutfitrecommendationsystem.favorite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class FavoriteControllerTests {

    private static final AtomicInteger USER_SEQUENCE = new AtomicInteger(8400);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FavoriteMapper favoriteMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void listsOnlyCurrentUsersFavoritesAsDisplayableProducts() throws Exception {
        AuthenticatedUser owner = registerAndLogin(nextUsername());
        AuthenticatedUser otherUser = registerAndLogin(nextUsername());
        insertFavorite(owner.userId(), 1001L);
        insertFavorite(otherUser.userId(), 1002L);

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].spuId").value(1001))
                .andExpect(jsonPath("$.data[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data[0].categoryName").isNotEmpty())
                .andExpect(jsonPath("$.data[0].salePrice").isNumber());
    }

    @Test
    void addsFavoriteFromJsonBodyAndReturnsCurrentDisplayableList() throws Exception {
        AuthenticatedUser user = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spuId\":1001}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].spuId").value(1001))
                .andExpect(jsonPath("$.data[0].name").isNotEmpty())
                .andExpect(jsonPath("$.data[0].salePrice").isNumber());
    }

    @Test
    void keepsUnavailableFavoritesInRecentOrderWithOneRowPerSpu() throws Exception {
        AuthenticatedUser user = registerAndLogin(nextUsername());
        insertFavorite(user.userId(), 1001L);
        insertFavorite(user.userId(), 1136L);
        insertFavorite(user.userId(), 1137L);

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].spuId").value(1137))
                .andExpect(jsonPath("$.data[0].availabilityStatus").value("unavailable"))
                .andExpect(jsonPath("$.data[0].salePrice").isNumber())
                .andExpect(jsonPath("$.data[1].spuId").value(1136))
                .andExpect(jsonPath("$.data[1].availabilityStatus").value("unavailable"))
                .andExpect(jsonPath("$.data[2].spuId").value(1001));
    }

    @Test
    void rejectsUnknownPositiveSpuIdInsteadOfWritingAnInvalidFavorite() throws Exception {
        AuthenticatedUser user = registerAndLogin(nextUsername());

        mockMvc.perform(post("/api/favorites")
                        .header("Authorization", "Bearer " + user.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spuId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("not_found"))
                .andExpect(jsonPath("$.message").value("product not found: 999999"));
    }

    @Test
    void deletingMissingFavoriteIsIdempotentAndReturnsCurrentDisplayableList() throws Exception {
        AuthenticatedUser user = registerAndLogin(nextUsername());
        insertFavorite(user.userId(), 1001L);

        mockMvc.perform(delete("/api/favorites/{spuId}", 999999L)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].spuId").value(1001))
                .andExpect(jsonPath("$.data[0].name").isNotEmpty());
    }

    @Test
    void deletesAnUnavailableFavoriteAfterItHasBeenReturnedToTheUser() throws Exception {
        AuthenticatedUser user = registerAndLogin(nextUsername());
        insertFavorite(user.userId(), 1137L);

        mockMvc.perform(get("/api/favorites")
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spuId").value(1137));

        mockMvc.perform(delete("/api/favorites/{spuId}", 1137L)
                        .header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private void insertFavorite(Long userId, Long spuId) {
        UserFavorite favorite = new UserFavorite();
        favorite.setUserId(userId);
        favorite.setSpuId(spuId);
        favoriteMapper.insert(favorite);
    }

    private AuthenticatedUser registerAndLogin(String username) throws Exception {
        String registerBody = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "StrongPassword123!",
                                  "email": "%s@example.com"
                                }
                                """.formatted(username, username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long userId = objectMapper.readTree(registerBody).path("data").path("userId").asLong();

        String loginBody = mockMvc.perform(post("/api/auth/login")
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
        JsonNode data = objectMapper.readTree(loginBody).path("data");
        return new AuthenticatedUser(userId, data.path("accessToken").asText());
    }

    private String nextUsername() {
        return "favorite_controller_user_" + USER_SEQUENCE.incrementAndGet();
    }

    private record AuthenticatedUser(Long userId, String token) {
    }
}
