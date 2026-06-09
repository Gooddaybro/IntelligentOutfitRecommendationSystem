package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class InternalInventoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsInventoryBySkuId() throws Exception {
        mockMvc.perform(get("/internal/inventory")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("skuId", "2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("TS-BASIC-001-BLK-L"))
                .andExpect(jsonPath("$.data.availableStock").value(8))
                .andExpect(jsonPath("$.data.inStock").value(true));
    }
}
