package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.repository.ProductQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ProductCatalogRepositoryTests {

    @Autowired
    private ProductQueryRepository repository;

    @Test
    void searchProductsFindsBasicTshirtByKeyword() {
        var results = repository.searchProducts("纯棉T恤");

        assertThat(results).extracting("spuCode").contains("TSHIRT_BASIC_001");
    }

    @Test
    void findSkuReturnsBlackLargeTshirt() {
        var sku = repository.findSku(1001L, "黑色", "L").orElseThrow();

        assertThat(sku.skuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(sku.salePrice().intValue()).isEqualTo(99);
    }

    @Test
    void findRecommendationCandidatesFiltersByStyleSeasonAndBudget() {
        var candidates = repository.findRecommendationCandidates("外套", "commute", "autumn", null, null, 400);

        assertThat(candidates).extracting("spuCode").contains("JACKET_COMMUTE_001");
    }
}
