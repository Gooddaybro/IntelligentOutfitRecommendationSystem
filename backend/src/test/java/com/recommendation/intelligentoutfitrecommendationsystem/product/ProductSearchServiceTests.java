package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchServiceTests {

    @Mock
    private ProductSearchGateway primaryGateway;
    @Mock
    private ProductSearchGateway fallbackGateway;
    @Mock
    private ProductMapper productMapper;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);
    private ProductSearchService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchService(primaryGateway, fallbackGateway, productMapper, 500, metrics);
    }

    @Test
    void hydratesPrimaryHitsInSearchOrderAndDropsMissingProducts() {
        ProductSearchCriteria criteria = new ProductSearchCriteria("冬季外套", "外套", 500);
        when(primaryGateway.search(criteria)).thenReturn(List.of(1106L, 1124L, 9999L));
        when(productMapper.findSearchItemsBySpuIds(List.of(1106L, 1124L, 9999L)))
                .thenReturn(List.of(item(1124L), item(1106L)));

        List<ProductSearchItem> result = service.search(" 冬季外套 ", " 外套 ");

        assertThat(result).extracting(ProductSearchItem::getSpuId).containsExactly(1106L, 1124L);
        verify(fallbackGateway, never()).search(criteria);
    }

    @Test
    void fallsBackOnlyWhenPrimaryIsUnavailable() {
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(primaryGateway.search(criteria)).thenThrow(new ProductSearchUnavailableException("offline"));
        when(fallbackGateway.search(criteria)).thenReturn(List.of(1002L));
        when(productMapper.findSearchItemsBySpuIds(List.of(1002L))).thenReturn(List.of(item(1002L)));

        List<ProductSearchItem> result = service.search("通勤", null);

        assertThat(result).extracting(ProductSearchItem::getSpuId).containsExactly(1002L);
        assertThat(registry.get("app.product.search.fallbacks")
                .tag("reason", "unavailable").counter().count())
                .isEqualTo(1);
    }

    @Test
    void propagatesProgrammingErrorsWithoutHidingThemBehindFallback() {
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(primaryGateway.search(criteria)).thenThrow(new IllegalArgumentException("bad query"));

        assertThatThrownBy(() -> service.search("通勤", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("bad query");
        verify(fallbackGateway, never()).search(criteria);
        assertThat(registry.find("app.product.search.fallbacks").counter()).isNull();
    }

    @Test
    void skipsHydrationForNoHits() {
        ProductSearchCriteria criteria = new ProductSearchCriteria(null, null, 500);
        when(primaryGateway.search(criteria)).thenReturn(List.of());

        assertThat(service.search(" ", null)).isEmpty();
        verify(productMapper, never()).findSearchItemsBySpuIds(List.of());
    }

    private ProductSearchItem item(Long spuId) {
        return new ProductSearchItem(
                spuId,
                "SPU_" + spuId,
                "商品" + spuId,
                "外套",
                "/product.jpg",
                "合身",
                BigDecimal.valueOf(199),
                BigDecimal.valueOf(299)
        );
    }
}
