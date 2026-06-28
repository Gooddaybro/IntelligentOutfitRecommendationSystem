package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashSet;

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
        assertThat(new HashSet<>(candidates.stream().map(candidate -> candidate.getSkuId()).toList()))
                .hasSize(candidates.size());
        assertThat(candidates)
                .filteredOn(candidate -> "JACKET_COMMUTE_001".equals(candidate.getSpuCode()))
                .first()
                .satisfies(candidate -> {
                    assertThat(candidate.getSkuId()).isNotNull();
                    assertThat(candidate.getSalePrice()).isEqualByComparingTo("299.00");
                    assertThat(candidate.getStockStatus()).isEqualTo("in_stock");
                    assertThat(candidate.getColor()).isNotBlank();
                    assertThat(candidate.getSize()).isNotBlank();
                    assertThat(candidate.getSkuCode()).isNotBlank();
                    assertThat(candidate.getAvailableStock()).isGreaterThan(0);
                    assertThat(candidate.getAttributeTags()).contains("适用场景");
                });
    }

    @Test
    void findRecommendationCandidatesReturnsFuzzyRecommendationAttributeTags() {
        var query = new RecommendationCandidateQuery("长裤", null, null, null, null, 300);

        var candidates = mapper.findRecommendationCandidates(query);

        assertThat(candidates)
                .filteredOn(candidate -> "PANTS_STRAIGHT_001".equals(candidate.getSpuCode()))
                .first()
                .satisfies(candidate -> assertThat(candidate.getAttributeTags())
                        .contains("腰线:中高腰")
                        .contains("下装版型:直筒")
                        .contains("视觉效果:显高")
                        .contains("视觉效果:显瘦")
                        .contains("视觉效果:遮肉")
                        .contains("场景:校园")
                        .contains("风格:基础款")
                        .contains("搭配难度:好搭"));
    }

    @Test
    void findRecommendationCandidatesReturnsExtendedCatalogAttributeTags() {
        var query = new RecommendationCandidateQuery("牛仔裤", null, null, null, null, 400);

        var candidates = mapper.findRecommendationCandidates(query);

        assertThat(candidates)
                .filteredOn(candidate -> "JEANS_STRAIGHT_DAILY_001".equals(candidate.getSpuCode()))
                .first()
                .satisfies(candidate -> assertThat(candidate.getAttributeTags())
                        .contains("腰线:中高腰")
                        .contains("下装版型:直筒")
                        .contains("视觉效果:显高")
                        .contains("视觉效果:显瘦")
                        .contains("视觉效果:遮肉")
                        .contains("材质特征:柔软")
                        .contains("搭配难度:好搭"));
    }
}
