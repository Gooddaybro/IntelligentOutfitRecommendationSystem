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
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateLiveFact;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchService;
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

    @Mock
    private ProductSearchService productSearchService;

    private ProductCatalogService service;
    private RecommendationCandidateQueryService candidateService;

    @BeforeEach
    void setUp() {
        CacheTtlProperties cacheTtlProperties = new CacheTtlProperties();
        cacheTtlProperties.setProductDetailJitterMinutes(0);
        cacheTtlProperties.setRecommendationCandidatesJitterMinutes(0);
        service = new ProductCatalogService(
                productMapper, productSearchService, redisCacheService, cacheTtlProperties);
        candidateService = new RecommendationCandidateQueryService(
                productMapper, redisCacheService, cacheTtlProperties);
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

        var products = service.searchProducts(" TSHIRT_BASIC_001 ", null);

        assertThat(products).extracting(ProductSearchItem::getSpuCode)
                .containsExactly("TSHIRT_BASIC_001");
        verify(productSearchService, never()).search(any(), any());
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    @Test
    void searchProductsCachesMapperResultOnMiss() {
        List<ProductSearchItem> mapperProducts = List.of(productSearchItem());
        when(redisCacheService.getList(anyString(), eq(ProductSearchItem.class)))
                .thenReturn(Optional.empty());
        when(productSearchService.search("TSHIRT_BASIC_001", null))
                .thenReturn(mapperProducts);

        var products = service.searchProducts(" TSHIRT_BASIC_001 ", null);

        assertThat(products).extracting(ProductSearchItem::getSpuCode)
                .containsExactly("TSHIRT_BASIC_001");
        verify(productSearchService).search("TSHIRT_BASIC_001", null);
        verify(redisCacheService).setValue(anyString(), eq(mapperProducts), any(Duration.class));
    }

    @Test
    void searchProductsPassesCategoryToMapperAndCacheKey() {
        List<ProductSearchItem> mapperProducts = List.of(productSearchItem());
        when(redisCacheService.getList(anyString(), eq(ProductSearchItem.class)))
                .thenReturn(Optional.empty());
        when(productSearchService.search("TSHIRT_BASIC_001", "\u5916\u5957"))
                .thenReturn(mapperProducts);

        service.searchProducts(" TSHIRT_BASIC_001 ", " \u5916\u5957 ");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService).getList(keyCaptor.capture(), eq(ProductSearchItem.class));
        assertThat(keyCaptor.getValue()).isEqualTo("product:search:tshirt_basic_001:\u5916\u5957");
        verify(productSearchService).search("TSHIRT_BASIC_001", "\u5916\u5957");
    }

    @Test
    void findRecommendationCandidatesUsesQueryDto() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);
        List<RecommendationCandidateSnapshot> snapshots = List.of(recommendationSnapshot(2101L));
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidateSnapshots(query)).thenReturn(snapshots);
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(recommendationLiveFact(2101L, 299, 11)));

        var candidates = candidateService.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuCode)
                .containsExactly("JACKET_COMMUTE_001");
        verify(productMapper).findRecommendationCandidateSnapshots(query);
        verify(redisCacheService).setValue(anyString(), eq(snapshots), any(Duration.class));
    }

    @Test
    void findRecommendationCandidatesNormalizesSkirtCategoryAlias() {
        var query = new RecommendationCandidateQuery("裙子", null, null, null, null, 400);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidateSnapshots(any()))
                .thenReturn(List.of(recommendationSnapshot(2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(recommendationLiveFact(2101L, 299, 11)));

        candidateService.findCandidates(query);

        ArgumentCaptor<RecommendationCandidateQuery> queryCaptor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productMapper).findRecommendationCandidateSnapshots(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCategory()).isEqualTo("半裙");
    }

    @Test
    void findRecommendationCandidatesHydratesCachedSnapshotsWithLiveFacts() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.of(List.of(recommendationSnapshot(2101L))));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(recommendationLiveFact(2101L, 259, 7)));

        var candidates = candidateService.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuCode)
                .containsExactly("JACKET_COMMUTE_001");
        assertThat(candidates.getFirst().getSalePrice()).isEqualByComparingTo("259");
        assertThat(candidates.getFirst().getAvailableStock()).isEqualTo(7);
        verify(productMapper, never()).findRecommendationCandidateSnapshots(query);
        verify(productMapper).findRecommendationCandidateLiveFacts(List.of(2101L));
        verify(redisCacheService, never()).setValue(any(), any(), any());
    }

    @Test
    void findRecommendationCandidatesFiltersUnavailableMissingAndOverBudgetLiveFacts() {
        var query = new RecommendationCandidateQuery("外套", null, null, null, null, 400);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.of(List.of(
                        recommendationSnapshot(2101L),
                        recommendationSnapshot(2102L),
                        recommendationSnapshot(2103L),
                        recommendationSnapshot(2104L)
                )));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L, 2102L, 2103L, 2104L)))
                .thenReturn(List.of(
                        recommendationLiveFact(2101L, 299, 0),
                        recommendationLiveFact(2102L, 500, 8),
                        recommendationLiveFact(2103L, 250, 5)
                ));

        var candidates = candidateService.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSkuId)
                .containsExactly(2103L);
        assertThat(candidates.getFirst().getStockStatus()).isEqualTo("in_stock");
        assertThat(candidates.getFirst().getSalePrice()).isEqualByComparingTo("250");
    }

    @Test
    void findRecommendationCandidatesUsesDifferentCacheKeysForDifferentGenderFilters() {
        var maleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "male");
        var femaleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "female");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidateSnapshots(maleQuery))
                .thenReturn(List.of(recommendationSnapshot(2101L)));
        when(productMapper.findRecommendationCandidateSnapshots(femaleQuery))
                .thenReturn(List.of(recommendationSnapshot(2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(recommendationLiveFact(2101L, 299, 11)));

        candidateService.findCandidates(maleQuery);
        candidateService.findCandidates(femaleQuery);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService, org.mockito.Mockito.times(2))
                .getList(keyCaptor.capture(), eq(RecommendationCandidateSnapshot.class));
        assertThat(keyCaptor.getAllValues()).hasSize(2);
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void findRecommendationCandidatesReusesStaticSnapshotCacheAcrossBudgets() {
        var lowBudgetQuery = new RecommendationCandidateQuery("外套", "commute", null, null, null, 200);
        var highBudgetQuery = new RecommendationCandidateQuery("外套", "commute", null, null, null, 500);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.of(List.of(recommendationSnapshot(2101L))));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(recommendationLiveFact(2101L, 299, 11)));

        candidateService.findCandidates(lowBudgetQuery);
        candidateService.findCandidates(highBudgetQuery);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService, org.mockito.Mockito.times(2))
                .getList(keyCaptor.capture(), eq(RecommendationCandidateSnapshot.class));
        assertThat(keyCaptor.getAllValues()).hasSize(2).allSatisfy(
                key -> assertThat(key).isEqualTo(keyCaptor.getAllValues().getFirst())
        );
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

    private RecommendationCandidateSnapshot recommendationSnapshot(Long skuId) {
        return new RecommendationCandidateSnapshot(
                1002L,
                skuId,
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
                "JACKET_COMMUTE_001-BLK-M",
                "适用场景:通勤"
        );
    }

    private RecommendationCandidateLiveFact recommendationLiveFact(
            Long skuId,
            int salePrice,
            int availableStock
    ) {
        return new RecommendationCandidateLiveFact(
                skuId,
                BigDecimal.valueOf(salePrice),
                BigDecimal.valueOf(salePrice),
                BigDecimal.valueOf(salePrice + 100L),
                availableStock,
                availableStock
        );
    }
}
