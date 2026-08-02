package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchIncrementalProjector;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchRebuildCompensator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchRebuildCompensatorTests {
    private final ProductSearchOutboxMapper outboxMapper = mock(ProductSearchOutboxMapper.class);
    private final ProductSearchIncrementalProjector projector = mock(ProductSearchIncrementalProjector.class);
    private final ProductSearchRebuildCompensator compensator =
            new ProductSearchRebuildCompensator(outboxMapper, projector);

    @Test
    void replaysDistinctProductsBetweenWatermarks() {
        when(outboxMapper.findMaxId()).thenReturn(15L);
        when(outboxMapper.findDistinctSpuIdsInRange(10L, 15L)).thenReturn(List.of(1001L, 1002L));

        compensator.compensateAfter(10L);

        verify(projector).project(1001L);
        verify(projector).project(1002L);
    }

    @Test
    void skipsQueryWhenNoEventWasCreatedDuringRebuild() {
        when(outboxMapper.findMaxId()).thenReturn(10L);

        compensator.compensateAfter(10L);

        verify(outboxMapper, never()).findDistinctSpuIdsInRange(10L, 10L);
        verify(projector, never()).project(org.mockito.ArgumentMatchers.anyLong());
    }
}
