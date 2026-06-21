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
class ProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void searchesPublicProductsWithoutInternalToken() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "TSHIRT_BASIC_001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].spuCode").value("TSHIRT_BASIC_001"))
                .andExpect(jsonPath("$.data[0].mainImageUrl").value("/images/products/tshirt-basic-main.svg"));
    }

    @Test
    void returnsPublicProductDetail() throws Exception {
        mockMvc.perform(get("/api/products/{spuId}", 1001))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.spuCode").value("TSHIRT_BASIC_001"))
                .andExpect(jsonPath("$.data.mainImageUrl").value("/images/products/tshirt-basic-main.svg"))
                .andExpect(jsonPath("$.data.styleTags", hasItem("casual")));
    }

    @Test
    void returnsPublicRecommendationCandidates() throws Exception {
        mockMvc.perform(get("/api/products/recommendation-candidates")
                        .param("category", "\u5916\u5957")
                        .param("style", "commute")
                        .param("season", "autumn")
                        .param("budgetMax", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].spuCode", hasItem("JACKET_COMMUTE_001")))
                .andExpect(jsonPath("$.data[0].mainImageUrl").value("/images/products/jacket-commute-main.svg"));
    }
}
