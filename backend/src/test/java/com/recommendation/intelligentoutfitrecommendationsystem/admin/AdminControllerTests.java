package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void restoreSeedProductStatus() {
        jdbcTemplate.update("UPDATE product_spu SET status = 'on_sale' WHERE id = 1001");
    }

    @Test
    void ordinaryUserCannotReadAdminOverview() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorReadsOverviewAndProducts() throws Exception {
        mockMvc.perform(get("/api/admin/overview")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.onSaleProducts").isNumber())
                .andExpect(jsonPath("$.data.skuCount").isNumber())
                .andExpect(jsonPath("$.data.lowStockCount").isNumber())
                .andExpect(jsonPath("$.data.pendingShipmentOrders").isNumber())
                .andExpect(jsonPath("$.data.afterSaleOrders").isNumber())
                .andExpect(jsonPath("$.data.orderCount").isNumber())
                .andExpect(jsonPath("$.data.paidAmount").isNumber())
                .andExpect(jsonPath("$.data.rangeLabel").value("\u6700\u8fd1 30 \u5929"));

        mockMvc.perform(get("/api/admin/products")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[*].spuCode", hasItem("TSHIRT_BASIC_001")))
                .andExpect(jsonPath("$.data[0].status").isNotEmpty())
                .andExpect(jsonPath("$.data[0].skuCount").isNumber())
                .andExpect(jsonPath("$.data[0].totalStock").isNumber());
    }

    @Test
    void administratorChangesProductStatusAndAdminListKeepsItVisible() throws Exception {
        mockMvc.perform(post("/api/admin/products/{spuId}/status", 1001)
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"OFF_SHELF\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.spuId").value(1001))
                .andExpect(jsonPath("$.data.spuCode").value("TSHIRT_BASIC_001"))
                .andExpect(jsonPath("$.data.status").value("OFF_SHELF"));

        mockMvc.perform(get("/api/admin/products")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status", hasItem("OFF_SHELF")));
    }
}
