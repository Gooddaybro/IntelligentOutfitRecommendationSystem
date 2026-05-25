package com.recommendation.intelligentoutfitrecommendationsystem.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class InternalProductControllerTests {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingInternalToken() throws Exception {
        mockMvc.perform(get("/internal/products/search").param("keyword", "TSHIRT_BASIC_001"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchesProductsWithInternalToken() throws Exception {
        mockMvc.perform(get("/internal/products/search")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .param("keyword", "TSHIRT_BASIC_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].spuCode").value("TSHIRT_BASIC_001"));
    }

    @Test
    void returnsProductDetail() throws Exception {
        mockMvc.perform(get("/internal/products/{spuId}", 1001)
                        .header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spuCode").value("TSHIRT_BASIC_001"))
                .andExpect(jsonPath("$.data.styleTags", hasItem("casual")));
    }

    @Test
    void findsBlackLargeSku() throws Exception {
        mockMvc.perform(get("/internal/skus/search")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .param("spuId", "1001")
                        .param("color", "\u9ed1\u8272")
                        .param("size", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("TS-BASIC-001-BLK-L"));
    }

    @Test
    void findsCommuteJacketRecommendationCandidate() throws Exception {
        mockMvc.perform(get("/internal/recommendation-candidates")
                        .header("X-Internal-Token", INTERNAL_TOKEN)
                        .param("category", "\u5916\u5957")
                        .param("style", "commute")
                        .param("season", "autumn")
                        .param("budgetMax", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].spuCode", hasItem("JACKET_COMMUTE_001")));
    }
}
