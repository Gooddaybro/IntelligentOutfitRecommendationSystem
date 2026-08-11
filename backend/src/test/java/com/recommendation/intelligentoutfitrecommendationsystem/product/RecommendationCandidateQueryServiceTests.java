package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateLiveFact;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.RecommendationEsRecallProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationCandidateQueryServiceTests {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private RedisCacheService redisCacheService;
    @Mock
    private ProductSearchGateway recallGateway;

    private CacheTtlProperties cacheTtlProperties;
    private RecommendationEsRecallProperties recallProperties;
    private SimpleMeterRegistry registry;
    private RecommendationCandidateQueryService service;

    @BeforeEach
    void setUp() {
        cacheTtlProperties = new CacheTtlProperties();
        cacheTtlProperties.setRecommendationCandidatesJitterMinutes(0);
        recallProperties = new RecommendationEsRecallProperties();
        registry = new SimpleMeterRegistry();
        service = new RecommendationCandidateQueryService(
                productMapper,
                redisCacheService,
                cacheTtlProperties,
                recallGateway,
                recallProperties,
                new ApplicationMetrics(registry));
    }

    @Test
    void disabledRecallUsesExistingMySqlCandidateSnapshots() {
        RecommendationCandidateQuery query = query("外套", "通勤外套");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(productMapper.findRecommendationCandidateSnapshots(any()))
                .thenReturn(List.of(snapshot(1002L, 2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(fact(2101L, 5, "299.00")));

        List<RecommendationCandidate> candidates = service.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuId).containsExactly(1002L);
        assertThat(registry.get("app.recommendation.recall.requests")
                .tags("engine", "mysql", "outcome", "disabled").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.recommendation.recall.candidates").summary().count())
                .isEqualTo(1);
        verify(recallGateway, never()).search(any());
        verify(productMapper).findRecommendationCandidateSnapshots(any());
        verify(productMapper, never()).findRecommendationCandidateSnapshotsBySpuIds(any(), any());
    }

    @Test
    void enabledRecallUsesEsSpuIdsBeforeMySqlSnapshotHydration() {
        recallProperties.setEnabled(true);
        RecommendationCandidateQuery query = query("外套", "通勤外套");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(recallGateway.search(new ProductSearchCriteria("通勤外套", "外套", 200)))
                .thenReturn(List.of(1002L, 1001L));
        when(productMapper.findRecommendationCandidateSnapshotsBySpuIds(any(), eq(List.of(1002L, 1001L))))
                .thenReturn(List.of(snapshot(1001L, 1101L), snapshot(1002L, 2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L, 1101L)))
                .thenReturn(List.of(fact(1101L, 50, "99.00"), fact(2101L, 1, "299.00")));

        List<RecommendationCandidate> candidates = service.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSpuId)
                .containsExactly(1002L, 1001L);
        assertThat(registry.get("app.recommendation.recall.requests")
                .tags("engine", "elasticsearch", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.recommendation.recall.spu.hits").summary().totalAmount())
                .isEqualTo(2);
        verify(productMapper, never()).findRecommendationCandidateSnapshots(any());
    }

    @Test
    void unavailableEsRecallFallsBackToMySqlSnapshots() {
        recallProperties.setEnabled(true);
        RecommendationCandidateQuery query = query(null, "通勤外套");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(recallGateway.search(new ProductSearchCriteria("通勤外套", null, 200)))
                .thenThrow(new ProductSearchUnavailableException("offline"));
        when(productMapper.findRecommendationCandidateSnapshots(any()))
                .thenReturn(List.of(snapshot(1002L, 2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(fact(2101L, 5, "299.00")));

        List<RecommendationCandidate> candidates = service.findCandidates(query);

        assertThat(candidates).extracting(RecommendationCandidate::getSkuId).containsExactly(2101L);
        assertThat(registry.get("app.recommendation.recall.requests")
                .tags("engine", "elasticsearch", "outcome", "unavailable").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.recommendation.recall.requests")
                .tags("engine", "mysql", "outcome", "fallback").counter().count())
                .isEqualTo(1);
        verify(productMapper).findRecommendationCandidateSnapshots(any());
        verify(redisCacheService, never()).setValue(anyString(), any(), any());
    }

    @Test
    void emptyEsRecallDoesNotQueryFullMySqlCandidatePool() {
        recallProperties.setEnabled(true);
        RecommendationCandidateQuery query = query("半裙", "不存在的风格");
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(recallGateway.search(new ProductSearchCriteria("不存在的风格", "半裙", 200)))
                .thenReturn(List.of());

        assertThat(service.findCandidates(query)).isEmpty();

        assertThat(registry.get("app.recommendation.recall.requests")
                .tags("engine", "elasticsearch", "outcome", "empty").counter().count())
                .isEqualTo(1);
        verify(productMapper, never()).findRecommendationCandidateSnapshots(any());
        verify(productMapper, never()).findRecommendationCandidateSnapshotsBySpuIds(any(), any());
        verify(productMapper, never()).findRecommendationCandidateLiveFacts(any());
    }

    @Test
    void activeRecallCacheKeyIncludesRecallText() {
        recallProperties.setEnabled(true);
        when(redisCacheService.getList(anyString(), eq(RecommendationCandidateSnapshot.class)))
                .thenReturn(Optional.empty());
        when(recallGateway.search(any())).thenReturn(List.of(1002L));
        when(productMapper.findRecommendationCandidateSnapshotsBySpuIds(any(), eq(List.of(1002L))))
                .thenReturn(List.of(snapshot(1002L, 2101L)));
        when(productMapper.findRecommendationCandidateLiveFacts(List.of(2101L)))
                .thenReturn(List.of(fact(2101L, 5, "299.00")));

        service.findCandidates(query("外套", "通勤外套"));
        service.findCandidates(query("外套", "硬朗外套"));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisCacheService, org.mockito.Mockito.times(2))
                .getList(keyCaptor.capture(), eq(RecommendationCandidateSnapshot.class));
        assertThat(keyCaptor.getAllValues()).hasSize(2).doesNotHaveDuplicates();
    }

    private RecommendationCandidateQuery query(String category, String recallText) {
        return new RecommendationCandidateQuery(category, null, null, null, null, null, null, recallText);
    }

    private RecommendationCandidateSnapshot snapshot(Long spuId, Long skuId) {
        return new RecommendationCandidateSnapshot(
                spuId,
                skuId,
                "SPU_" + spuId,
                "商品" + spuId,
                "外套",
                "/product.jpg",
                "合身",
                "黑色",
                "M",
                "聚酯纤维",
                "autumn",
                "commute",
                "SKU_" + skuId,
                "场景:通勤");
    }

    private RecommendationCandidateLiveFact fact(Long skuId, Integer stock, String price) {
        return new RecommendationCandidateLiveFact(
                skuId,
                new BigDecimal(price),
                new BigDecimal(price),
                new BigDecimal(price),
                stock,
                stock);
    }
}
