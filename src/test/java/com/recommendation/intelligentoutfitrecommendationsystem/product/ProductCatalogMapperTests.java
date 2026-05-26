package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ProductCatalogMapperTests {

    @Autowired
    private ProductMapper mapper;

    @Test
    void searchProductsFindsBasicTshirtByKeyword() {
        var results = mapper.searchProducts("基础款纯棉T恤");

        assertThat(results).extracting("spuCode").contains("TSHIRT_BASIC_001");
    }

    @Test
    void findSkuReturnsBlackLargeTshirt() {
        var sku = mapper.findSku(1001L, "黑色", "L");

        assertThat(sku.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(sku.getSalePrice().intValue()).isEqualTo(99);
    }

    @Test
    void findProductDetailBaseReturnsBasicFields() {
        var detail = mapper.findProductDetailBase(1001L);

        assertThat(detail.getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        assertThat(detail.getName()).contains("T恤");
    }

    @Test
    void findsProductMultiValueAttributes() {
        assertThat(mapper.findMaterials(1001L)).contains("纯棉");
        assertThat(mapper.findSeasons(1001L)).contains("summer");
        assertThat(mapper.findStyleTags(1001L)).contains("casual");
        assertThat(mapper.findAttributes(1001L)).extracting("attrName").contains("厚度");
    }

    @Test
    void findRecommendationCandidatesFiltersByStyleSeasonAndBudget() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);

        var candidates = mapper.findRecommendationCandidates(query);

        assertThat(candidates).extracting("spuCode").contains("JACKET_COMMUTE_001");
    }
}
