package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexAdminGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexDescriptor;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexLifecycleService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRetentionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexLifecycleServiceTests {

    @Mock
    private ProductSearchIndexAdminGateway gateway;

    private ProductSearchIndexLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new ProductSearchIndexLifecycleService(
                gateway, new ProductSearchIndexRetentionPolicy("product_", 2), "product_current");
    }

    @Test
    void deletesOnlyFailedIndexThatMatchesManagedName() {
        service.deleteFailedIndex("product_20260720120000");

        verify(gateway).deleteIndex("product_20260720120000");
    }

    @Test
    void refusesToDeleteFailedIndexOutsideManagedNamePattern() {
        service.deleteFailedIndex("product_v1");

        verify(gateway, never()).deleteIndex("product_v1");
    }

    @Test
    void neverDeletesFailedIndexWhenAliasAlreadyPointsToIt() {
        when(gateway.findAliasTargets("product_current"))
                .thenReturn(Set.of("product_20260720120000"));

        service.deleteFailedIndex("product_20260720120000");

        verify(gateway, never()).deleteIndex("product_20260720120000");
    }

    @Test
    void deletesOnlyHistoryOutsideConfiguredRetentionWindow() {
        when(gateway.listIndices("product_*")).thenReturn(List.of(
                index("product_20260720140000", 4),
                index("product_20260720130000", 3),
                index("product_20260720120000", 2),
                index("product_20260720110000", 1),
                index("product_v1", 0)
        ));
        when(gateway.findAliasTargets("product_current"))
                .thenReturn(Set.of("product_20260720140000"));

        service.pruneHistory();

        verify(gateway).deleteIndex("product_20260720110000");
        verify(gateway, never()).deleteIndex("product_20260720140000");
        verify(gateway, never()).deleteIndex("product_v1");
    }

    private ProductSearchIndexDescriptor index(String name, long order) {
        return new ProductSearchIndexDescriptor(name, Instant.ofEpochSecond(order));
    }
}
