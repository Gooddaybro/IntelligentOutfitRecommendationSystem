package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRow;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchIncrementalProjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductSearchIncrementalProjectorTests {
    @Mock
    private ElasticsearchClient client;
    @Mock
    private ProductMapper productMapper;
    private ProductSearchIncrementalProjector projector;

    @BeforeEach
    void setUp() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexAlias("product_current");
        projector = new ProductSearchIncrementalProjector(
                client, productMapper, properties,
                Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void upsertsCurrentMysqlFactIntoAlias() throws IOException {
        when(productMapper.findSearchIndexRowBySpuId(1001L)).thenReturn(row());

        projector.project(1001L);

        ArgumentCaptor<IndexRequest<?>> request = ArgumentCaptor.forClass(IndexRequest.class);
        verify(client).index(request.capture());
        assertThat(request.getValue().index()).isEqualTo("product_current");
        assertThat(request.getValue().id()).isEqualTo("1001");
        verify(client, never()).delete(any(DeleteRequest.class));
    }

    @Test
    void deletesDocumentWhenProductNoLongerExists() throws IOException {
        projector.project(404L);

        ArgumentCaptor<DeleteRequest> request = ArgumentCaptor.forClass(DeleteRequest.class);
        verify(client).delete(request.capture());
        assertThat(request.getValue().index()).isEqualTo("product_current");
        assertThat(request.getValue().id()).isEqualTo("404");
    }

    @Test
    void translatesTransportFailureIntoRetryableSignal() throws IOException {
        when(productMapper.findSearchIndexRowBySpuId(1001L)).thenReturn(row());
        when(client.index(any(IndexRequest.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> projector.project(1001L))
                .isInstanceOf(ProductSearchUnavailableException.class);
    }

    private ProductSearchIndexRow row() {
        return new ProductSearchIndexRow(
                1001L, "TSHIRT_001", "基础款T恤", "纯棉短袖", "T恤", "合身",
                "纯棉", "casual", "日常", "summer", "on_sale");
    }
}
