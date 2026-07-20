package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexLifecycleService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRow;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchRebuildCompensator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIndexServiceTests {

    @Mock
    private ElasticsearchClient client;
    @Mock
    private ElasticsearchIndicesClient indicesClient;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductSearchIndexLifecycleService lifecycleService;
    @Mock
    private ProductSearchRebuildCompensator rebuildCompensator;

    private ProductSearchIndexService service;

    @BeforeEach
    void setUp() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexPrefix("product_");
        properties.setIndexAlias("product_current");
        properties.setBulkBatchSize(200);
        service = new ProductSearchIndexService(client, productMapper, properties, lifecycleService);
    }

    @Test
    void deletesNewIndexWhenBulkWriteFails() throws IOException {
        prepareRowsAndCreatedIndex();
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("bulk failed"));

        assertThatThrownBy(service::rebuild).isInstanceOf(ProductSearchUnavailableException.class);

        verify(lifecycleService).deleteFailedIndex(org.mockito.ArgumentMatchers.startsWith("product_"));
        verify(lifecycleService, never()).pruneHistory();
    }

    @Test
    void doesNotDeleteWhenIndexCreationWasNotConfirmed() throws IOException {
        when(client.indices()).thenReturn(indicesClient);
        when(productMapper.findAllSearchIndexRows()).thenReturn(List.of(row()));
        when(indicesClient.create(any(java.util.function.Function.class)))
                .thenThrow(new IOException("create failed"));

        assertThatThrownBy(service::rebuild).isInstanceOf(ProductSearchUnavailableException.class);

        verify(lifecycleService, never()).deleteFailedIndex(any());
    }

    @Test
    void keepsOriginalFailureWhenFailedIndexCleanupAlsoFails() throws IOException {
        prepareRowsAndCreatedIndex();
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("bulk failed"));
        doThrow(new IllegalStateException("cleanup failed"))
                .when(lifecycleService).deleteFailedIndex(any());

        assertThatThrownBy(service::rebuild)
                .isInstanceOf(ProductSearchUnavailableException.class)
                .hasMessage("商品搜索索引重建失败")
                .satisfies(error -> assertThat(error.getSuppressed())
                        .extracting(Throwable::getMessage)
                        .contains("cleanup failed"));
    }

    @Test
    void returnsSuccessWhenPostSwitchRetentionCleanupFails() throws IOException {
        prepareSuccessfulRebuild();
        doThrow(new IllegalStateException("cleanup failed")).when(lifecycleService).pruneHistory();

        var result = service.rebuild();

        assertThat(result.documentCount()).isEqualTo(1);
        verify(lifecycleService).pruneHistory();
        verify(lifecycleService, never()).deleteFailedIndex(any());
    }

    @Test
    void compensatesEventsCreatedDuringFullRebuild() throws IOException {
        service = new ProductSearchIndexService(
                client, productMapper, properties(), lifecycleService, Optional.of(rebuildCompensator));
        when(rebuildCompensator.captureWatermark()).thenReturn(10L);
        prepareSuccessfulRebuild();

        service.rebuild();

        verify(rebuildCompensator).compensateAfter(10L);
    }

    private void prepareRowsAndCreatedIndex() throws IOException {
        when(client.indices()).thenReturn(indicesClient);
        when(productMapper.findAllSearchIndexRows()).thenReturn(List.of(row()));
        when(indicesClient.create(any(java.util.function.Function.class)))
                .thenReturn(mock(CreateIndexResponse.class));
    }

    private void prepareSuccessfulRebuild() throws IOException {
        prepareRowsAndCreatedIndex();
        BulkResponse bulkResponse = mock(BulkResponse.class);
        when(bulkResponse.errors()).thenReturn(false);
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
        when(client.count(any(java.util.function.Function.class)))
                .thenReturn(CountResponse.of(builder -> builder.count(1).shards(shards -> shards
                        .failed(0).successful(1).total(1))));
    }

    private ProductSearchIndexRow row() {
        return new ProductSearchIndexRow(
                1001L, "TSHIRT_001", "基础款T恤", "纯棉短袖", "上装", "合身",
                "纯棉", "casual", "日常", "summer", "on_sale");
    }

    private ElasticsearchSearchProperties properties() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexPrefix("product_");
        properties.setIndexAlias("product_current");
        properties.setBulkBatchSize(200);
        return properties;
    }
}
