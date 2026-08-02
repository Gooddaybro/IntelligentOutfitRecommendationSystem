package com.recommendation.intelligentoutfitrecommendationsystem.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminCategoryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.mapper.AdminProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminProductWrite;
import com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminSkuWrite;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
class AdminProductMapperTests {
    @Autowired
    private AdminProductMapper mapper;

    @Test
    @Transactional
    void insertsProductAndSkuWithGeneratedKeys() {
        AdminProductWrite product = new AdminProductWrite(
                "ADMIN_MAPPER_TEST", "Mapper test product", 2L,
                "test", null, "draft");
        assertThat(mapper.insertProduct(product)).isEqualTo(1);
        assertThat(product.getId()).isPositive();

        AdminSkuWrite sku = new AdminSkuWrite(
                "ADMIN_MAPPER_TEST-DEFAULT", product.getId(), 1L, 1L,
                new BigDecimal("10.00"), new BigDecimal("20.00"), "off_sale");
        assertThat(mapper.insertDefaultSku(sku)).isEqualTo(1);
        assertThat(sku.getId()).isPositive();
    }

    @Test
    void readsProductCategoriesAndStyleTags() {
        assertThat(mapper.findProductById(1001L).getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        assertThat(mapper.findCategories()).extracting(AdminCategoryResponse::id).contains(1L, 2L);
        assertThat(mapper.findProductStyleTags(1001L)).isNotNull();
    }
}
