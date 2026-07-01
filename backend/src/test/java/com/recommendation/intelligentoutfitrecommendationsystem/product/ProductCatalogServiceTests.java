package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCatalogServiceTests {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private RedisCacheService redisCacheService;

    private ProductCatalogService service;

    @BeforeEach
    void setUp() {
        CacheTtlProperties cacheTtlProperties = new CacheTtlProperties();
        cacheTtlProperties.setProductDetailJitterMinutes(0);
        cacheTtlProperties.setRecommendationCandidatesJitterMinutes(0);
        service = new ProductCatalogService(productMapper, redisCacheService, cacheTtlProperties);
    }

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
    void searchProductsReturnsCachedListWithoutQueryingMapper() {
        List<ProductSearchItem> cachedProducts = List.of(productSearchItem());
        when(redisCacheService.getList(anyString(), eq(ProductSearchItem.class)))
                .thenReturn(Optional.of(cachedProducts));

        var products = service.searchProducts(" TSHIRT_BASIC_001 ");

        assertThat(products).extracting(ProductSearchItem::getSpuCode)
                .containsExactly("TSHIRT_BASIC_001");
        verify(productMapper, never()).searchProducts(any());
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    @Test
    void searchProductsCachesMapperResultOnMiss() {
        List<ProductSearchItem> mapperProducts = List.of(productSearchItem());
        when(redisCacheService.getList(anyString(), eq(ProductSearchItem.class)))
                .thenReturn(Optional.empty());
        when(productMapper.searchProducts("TSHIRT_BASIC_001"))
                .thenReturn(mapperProducts);

        var products = service.searchProducts(" TSHIRT_BASIC_001 ");

        assertThat(products).extracting(ProductSearchItem::getSpuCode)
                .containsExactly("TSHIRT_BASIC_001");
        verify(productMapper).searchProducts("TSHIRT_BASIC_001");
        verify(redisCacheService).setValue(anyString(), eq(mapperProducts), any(Duration.class));
    }

    @Test
    void findRecommendationCandidatesUsesQueryDto() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidate.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidates(query))
                .thenReturn(List.of(recommendationCandidate()));

        var candidates = service.findRecommendationCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuCode)
                .containsExactly("JACKET_COMMUTE_001");
        verify(productMapper).findRecommendationCandidates(query);
        verify(redisCacheService).setValue(anyString(), eq(candidates), any(Duration.class));
    }

    @Test
    void findRecommendationCandidatesReturnsCachedListWithoutQueryingMapper() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);
        List<RecommendationCandidate> cachedCandidates = List.of(recommendationCandidate());
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidate.class)))
                .thenReturn(Optional.of(cachedCandidates));

        var candidates = service.findRecommendationCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuCode)
                .containsExactly("JACKET_COMMUTE_001");
        verify(productMapper, never()).findRecommendationCandidates(query);
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    @Test
    void findRecommendationCandidatesUsesDifferentCacheKeysForDifferentGenderFilters() {
        var maleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "male");
        var femaleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "female");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidate.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidates(maleQuery))
                .thenReturn(List.of(recommendationCandidate()));
        when(productMapper.findRecommendationCandidates(femaleQuery))
                .thenReturn(List.of(recommendationCandidate()));

        service.findRecommendationCandidates(maleQuery);
        service.findRecommendationCandidates(femaleQuery);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService, org.mockito.Mockito.times(2))
                .getList(keyCaptor.capture(), eq(RecommendationCandidate.class));
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void getProductDetailAssemblesMultiValueAttributes() {
        when(redisCacheService.getValue(CacheKeyConstants.productDetail(1001L), ProductDetail.class))
                .thenReturn(Optional.empty());
        when(productMapper.findProductDetailBase(1001L))
                .thenReturn(productDetail());
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
        verify(redisCacheService).setValue(
                eq(CacheKeyConstants.productDetail(1001L)),
                eq(detail),
                any(Duration.class)
        );
    }

    @Test
    void getProductDetailReturnsCachedDetailWithoutQueryingMapper() {
        ProductDetail cachedDetail = productDetail();
        cachedDetail.setMaterials(List.of("纯棉"));
        when(redisCacheService.getValue(CacheKeyConstants.productDetail(1001L), ProductDetail.class))
                .thenReturn(Optional.of(cachedDetail));

        var detail = service.getProductDetail(1001L);

        assertThat(detail.getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        verify(productMapper, never()).findProductDetailBase(1001L);
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    private ProductDetail productDetail() {
        return new ProductDetail(
                1001L,
                "TSHIRT_BASIC_001",
                "基础款纯棉T恤",
                "T恤",
                "100%纯棉基础款T恤，适合日常内搭和单穿。",
                "/images/products/tshirt-basic-main.svg",
                "合身",
                null,
                null,
                null,
                null,
                BigDecimal.valueOf(99),
                BigDecimal.valueOf(99)
        );
    }

    private ProductSearchItem productSearchItem() {
        return new ProductSearchItem(
                1001L,
                "TSHIRT_BASIC_001",
                "Basic T-shirt",
                "T-shirt",
                "/images/products/tshirt-basic-main.svg",
                "regular",
                BigDecimal.valueOf(99),
                BigDecimal.valueOf(99)
        );
    }

    private RecommendationCandidate recommendationCandidate() {
        return new RecommendationCandidate(
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
                11,
                "JACKET_COMMUTE_001-BLK-M",
                11,
                "适用场景:通勤"
        );
    }
}
