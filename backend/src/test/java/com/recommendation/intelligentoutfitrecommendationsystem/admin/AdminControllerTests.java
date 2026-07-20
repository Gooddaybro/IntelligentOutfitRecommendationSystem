package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
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
@SpringBootTest(properties = "app.product-search-sync.enabled=true")
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
        jdbcTemplate.update("UPDATE product_sku SET status = 'on_sale' WHERE spu_id = 1001");
        jdbcTemplate.update("UPDATE category SET name = '上衣', status = 'active' WHERE id = 1");
        jdbcTemplate.update("UPDATE category SET name = 'T恤', status = 'active' WHERE id = 2");
        jdbcTemplate.update("UPDATE inventory SET available_stock = 6 WHERE sku_id = 2001");
        jdbcTemplate.update("UPDATE sales_order SET status = 'PAID' WHERE order_no = 'ORDDEMO9001PAID'");
        jdbcTemplate.update("UPDATE user_account SET status = 'active' WHERE id = 9001");
        safeUpdate("DELETE FROM product_sku WHERE spu_id IN (SELECT id FROM product_spu WHERE spu_code = 'ADMIN_TEST_001')");
        safeUpdate("DELETE FROM product_style_tag WHERE spu_id IN (SELECT id FROM product_spu WHERE spu_code = 'ADMIN_TEST_001')");
        safeUpdate("DELETE FROM product_image WHERE spu_id IN (SELECT id FROM product_spu WHERE spu_code = 'ADMIN_TEST_001')");
        safeUpdate("DELETE FROM product_spu WHERE spu_code = 'ADMIN_TEST_001'");
        safeUpdate("DELETE FROM admin_inventory_adjustment");
        safeUpdate("DELETE FROM order_shipment");
        safeUpdate("DELETE FROM admin_audit_log");
        safeUpdate("DELETE FROM product_search_outbox");
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

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_search_outbox WHERE spu_id = 1001", Integer.class);
        org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(1);

        mockMvc.perform(get("/api/admin/products")
                        .with(jwt().jwt(token -> token.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status", hasItem("OFF_SHELF")));
    }

    @Test
    void administratorCreatesAndUpdatesProduct() throws Exception {
        String createBody = """
                {
                  "spuCode":"ADMIN_TEST_001",
                  "name":"Admin Test Product",
                  "categoryId":2,
                  "categoryName":"T",
                  "minPrice":123.00,
                  "maxPrice":123.00,
                  "status":"DRAFT",
                  "mainImageUrl":"/images/products/admin-test.svg",
                  "description":"created from admin test",
                  "styleTags":["casual"]
                }
                """;

        String created = mockMvc.perform(post("/api/admin/products")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.spuCode").value("ADMIN_TEST_001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.skuCount").isNumber())
                .andReturn().getResponse().getContentAsString();

        long spuId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created).path("data").path("spuId").asLong();

        String updateBody = """
                {
                  "spuCode":"ADMIN_TEST_001",
                  "name":"Admin Test Product Updated",
                  "categoryId":2,
                  "categoryName":"T",
                  "minPrice":129.00,
                  "maxPrice":129.00,
                  "status":"ON_SALE",
                  "mainImageUrl":"/images/products/admin-test.svg",
                  "description":"updated from admin test",
                  "styleTags":["minimal"]
                }
                """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/products/{spuId}", spuId)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Admin Test Product Updated"))
                .andExpect(jsonPath("$.data.status").value("ON_SALE"));

        Integer eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_search_outbox WHERE spu_id = ?", Integer.class, spuId);
        org.assertj.core.api.Assertions.assertThat(eventCount).isEqualTo(2);
    }

    @Test
    void administratorManagesCategoriesAndInventory() throws Exception {
        mockMvc.perform(get("/api/admin/categories").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].id", hasItem(1)))
                .andExpect(jsonPath("$.data[0].enabled").isBoolean())
                .andExpect(jsonPath("$.data[0].productCount").isNumber());

        int affectedProducts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_spu WHERE category_id = 2", Integer.class);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/admin/categories/{id}", 2)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":2,\"name\":\"测试T恤\",\"level\":2,\"sortOrder\":1,\"enabled\":false,\"productCount\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.name").value("测试T恤"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        Integer categoryEventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_search_outbox", Integer.class);
        org.assertj.core.api.Assertions.assertThat(categoryEventCount).isEqualTo(affectedProducts);

        mockMvc.perform(get("/api/admin/inventory").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].skuId", hasItem(2001)))
                .andExpect(jsonPath("$.data[0].lowStockThreshold").isNumber())
                .andExpect(jsonPath("$.data[0].status").isNotEmpty());

        mockMvc.perform(post("/api/admin/inventory/{skuId}/adjustments", 2001)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStock\":15,\"reason\":\"manual count\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuId").value(2001))
                .andExpect(jsonPath("$.data.availableStock").value(15))
                .andExpect(jsonPath("$.data.lastAdjustment.beforeStock").value(6))
                .andExpect(jsonPath("$.data.lastAdjustment.afterStock").value(15))
                .andExpect(jsonPath("$.data.lastAdjustment.reason").value("manual count"));

        // 库存不属于当前搜索文档，调整库存不应产生额外同步事件。
        Integer eventCountAfterInventory = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_search_outbox", Integer.class);
        org.assertj.core.api.Assertions.assertThat(eventCountAfterInventory).isEqualTo(categoryEventCount);
    }

    @Test
    void administratorShipsPaidOrderAndManagesUsers() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].orderNo", hasItem("ORDDEMO9001PAID")))
                .andExpect(jsonPath("$.data[0].itemCount").isNumber())
                .andExpect(jsonPath("$.data[0].paymentStatus").isNotEmpty());

        mockMvc.perform(post("/api/admin/orders/{orderNo}/ship", "ORDDEMO9001PAID")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"SF Express\",\"trackingNo\":\"SF20260717001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("ORDDEMO9001PAID"))
                .andExpect(jsonPath("$.data.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.shipment.carrier").value("SF Express"))
                .andExpect(jsonPath("$.data.availableActions[*]", org.hamcrest.Matchers.not(hasItem("SHIP"))));

        mockMvc.perform(post("/api/admin/orders/{orderNo}/ship", "ORDDEMO9001UNPAID")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"carrier\":\"SF Express\",\"trackingNo\":\"SF20260717002\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/admin/users").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].username", hasItem("demo_commute_male")))
                .andExpect(jsonPath("$.data[0].orderCount").isNumber())
                .andExpect(jsonPath("$.data[0].paidAmount").isNumber());

        mockMvc.perform(post("/api/admin/users/{userId}/status", 9001)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(9001))
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void administratorReadsAnalyticsAndAuditLogs() throws Exception {
        mockMvc.perform(post("/api/admin/inventory/{skuId}/adjustments", 2001)
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetStock\":9,\"reason\":\"audit seed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/analytics").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rangeLabel").value("\u6700\u8fd1 30 \u5929"))
                .andExpect(jsonPath("$.data.orderCount").isNumber())
                .andExpect(jsonPath("$.data.paidAmount").isNumber())
                .andExpect(jsonPath("$.data.funnel.exposed").isNumber())
                .andExpect(jsonPath("$.data.trend").isArray())
                .andExpect(jsonPath("$.data.hotProducts").isArray())
                .andExpect(jsonPath("$.data.categoryTrend").isArray());

        mockMvc.perform(get("/api/admin/audit-logs").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].action", hasItem("ADJUST_STOCK")))
                .andExpect(jsonPath("$.data[0].result").value("SUCCESS"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor adminJwt() {
        return jwt().jwt(token -> token.subject("1"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private void safeUpdate(String sql) {
        try {
            jdbcTemplate.update(sql);
        } catch (DataAccessException ignored) {
        }
    }
}
