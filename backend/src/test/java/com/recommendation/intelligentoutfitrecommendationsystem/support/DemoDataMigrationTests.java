package com.recommendation.intelligentoutfitrecommendationsystem.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class DemoDataMigrationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void extendedDemoDataCoversRecommendationCommerceAndPaymentStates() {
        Integer addedSpuCount = count("product_spu", "id >= 1101");
        Integer addedSkuCount = count("product_sku", "id >= 11011");
        Integer demoUserCount = count("user_account", "id BETWEEN 9001 AND 9008");
        Integer demoCartCount = count("cart_item", "user_id BETWEEN 9001 AND 9008");
        Integer demoOrderCount = count("sales_order", "id BETWEEN 9201 AND 9208");
        Integer demoPaymentCount = count("payment", "id BETWEEN 9301 AND 9308");
        Integer callbackLogCount = count("payment_callback_log", "id BETWEEN 9401 AND 9408");
        Integer lockedInventoryCount = count("inventory", "locked_stock > 0");
        Integer soldInventoryCount = count("inventory", "sold_stock > 0");
        Integer zeroStockSkuCount = count("inventory", "available_stock = 0");

        assertThat(addedSpuCount).isGreaterThanOrEqualTo(37);
        assertThat(addedSkuCount).isGreaterThanOrEqualTo(220);
        assertThat(demoUserCount).isEqualTo(8);
        assertThat(demoCartCount).isGreaterThanOrEqualTo(8);
        assertThat(demoOrderCount).isGreaterThanOrEqualTo(6);
        assertThat(demoPaymentCount).isGreaterThanOrEqualTo(2);
        assertThat(callbackLogCount).isGreaterThanOrEqualTo(4);
        assertThat(lockedInventoryCount).isPositive();
        assertThat(soldInventoryCount).isPositive();
        assertThat(zeroStockSkuCount).isPositive();

        List<String> statuses = jdbcTemplate.queryForList("""
                SELECT DISTINCT status
                FROM sales_order
                WHERE id BETWEEN 9201 AND 9208
                ORDER BY status
                """, String.class);
        assertThat(statuses).contains("UNPAID", "PAID", "CANCELLED", "CLOSED");

        List<String> formalAutumnCandidates = jdbcTemplate.queryForList("""
                SELECT p.spu_code
                FROM product_spu p
                JOIN product_sku sku ON sku.spu_id = p.id
                JOIN inventory inv ON inv.sku_id = sku.id
                JOIN product_style_tag pst ON pst.spu_id = p.id
                JOIN style_tag st ON st.id = pst.style_tag_id
                JOIN product_season ps ON ps.spu_id = p.id
                JOIN season se ON se.id = ps.season_id
                JOIN fit_type f ON f.id = p.fit_type_id
                WHERE p.status = 'on_sale'
                  AND sku.status = 'on_sale'
                  AND inv.available_stock > 0
                  AND st.code = 'formal'
                  AND se.code = 'autumn'
                  AND f.code = 'slim'
                  AND sku.sale_price <= 800
                GROUP BY p.spu_code
                ORDER BY p.spu_code
                """, String.class);
        assertThat(formalAutumnCandidates).contains("BLAZER_FORMAL_001");
    }

    private Integer count(String tableName, String whereClause) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + tableName + " WHERE " + whereClause,
                Integer.class
        );
    }
}
