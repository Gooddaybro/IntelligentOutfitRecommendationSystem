package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTests {

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private ProductCatalogService service;

    @Test
    void findSkuNormalizesSizeBeforeQueryingMapper() {
        when(productMapper.findSku(1001L, "黑色", "L"))
                .thenReturn(new SkuSearchItem(2003L, "TS-BASIC-001-BLK-L", 1001L, "基础款纯棉T恤", "黑色", "L", BigDecimal.valueOf(99), "on_sale"));

        var sku = service.findSku(1001L, "黑色", " l ");

        assertThat(sku.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        verify(productMapper).findSku(1001L, "黑色", "L");
    }

    @Test
    void findSkuRejectsBlankColor() {
        assertThatThrownBy(() -> service.findSku(1001L, " ", "L"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("color must not be blank");
    }

    @Test
    void findRecommendationCandidatesUsesQueryDto() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);
        when(productMapper.findRecommendationCandidates(query))
                .thenReturn(List.of(new RecommendationCandidate(
                        1002L,
                        2101L,
                        "JACKET_COMMUTE_001",
                        "通勤夹克",
                        "外套",
                        "https://example.com/jacket.jpg",
                        "regular",
                        "黑色",
                        "M",
                        "聚酯纤维",
                        "autumn",
                        "commute",
                        BigDecimal.valueOf(299),
                        "in_stock",
                        BigDecimal.valueOf(299),
                        BigDecimal.valueOf(399),
                        11
                )));

        var candidates = service.findRecommendationCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuCode)
                .containsExactly("JACKET_COMMUTE_001");
        verify(productMapper).findRecommendationCandidates(query);
    }

    @Test
    void getProductDetailAssemblesMultiValueAttributes() {
        when(productMapper.findProductDetailBase(1001L))
                .thenReturn(new ProductDetail(
                        1001L,
                        "TSHIRT_BASIC_001",
                        "基础款纯棉T恤",
                        "T恤",
                        "100%纯棉基础款T恤，适合日常内搭和单穿。",
                        "/images/products/tshirt-basic-main.jpg",
                        "合身",
                        null,
                        null,
                        null,
                        null,
                        BigDecimal.valueOf(99),
                        BigDecimal.valueOf(99)
                ));
        when(productMapper.findMaterials(1001L)).thenReturn(List.of("纯棉"));
        when(productMapper.findSeasons(1001L)).thenReturn(List.of("summer", "all_season"));
        when(productMapper.findStyleTags(1001L)).thenReturn(List.of("casual", "minimal"));
        when(productMapper.findAttributes(1001L))
                .thenReturn(List.of(new ProductAttributeItem("厚度", "常规")));

        var detail = service.getProductDetail(1001L);

        assertThat(detail.getMaterials()).containsExactly("纯棉");
        assertThat(detail.getSeasons()).containsExactly("summer", "all_season");
        assertThat(detail.getStyleTags()).containsExactly("casual", "minimal");
        assertThat(detail.getAttributes()).containsEntry("厚度", "常规");
    }
}
