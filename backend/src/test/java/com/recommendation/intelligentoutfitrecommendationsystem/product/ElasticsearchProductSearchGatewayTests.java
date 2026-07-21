package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchGatewayTests {

    @Mock
    private ElasticsearchClient client;
    @Mock
    private SearchResponse<Void> response;
    @Mock
    private HitsMetadata<Void> hits;
    @Mock
    private ElasticsearchException elasticsearchException;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);
    private ElasticsearchProductSearchGateway gateway;

    @BeforeEach
    void setUp() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexAlias("product_current");
        gateway = new ElasticsearchProductSearchGateway(client, properties, metrics);
    }

    @Test
    void searchesAliasWithWeightedFieldsAndReturnsHitOrder() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(response);
        when(response.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of(hit("1106"), hit("1124")));

        assertThat(gateway.search(new ProductSearchCriteria("黑色硬朗外套", "外套", 20)))
                .containsExactly(1106L, 1124L);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(requestCaptor.capture(), eq(Void.class));
        SearchRequest request = requestCaptor.getValue();
        assertThat(request.index()).containsExactly("product_current");
        assertThat(request.size()).isEqualTo(20);
        assertThat(request.query().bool().must().getFirst().multiMatch().fields())
                .containsExactly(
                        "name.smartcn^5",
                        "styles.search^3",
                        "category.search^2",
                        "scenes.search^2",
                        "materials.search^1.5",
                        "description.smartcn");
        assertThat(request.query().bool().filter()).hasSize(2);
        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.engine.duration")
                .tags("engine", "elasticsearch", "outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    void translatesTransportFailureIntoFallbackSignal() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> gateway.search(new ProductSearchCriteria("外套", null, 20)))
                .isInstanceOf(ProductSearchUnavailableException.class)
                .hasMessageContaining("Elasticsearch");
        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "unavailable").counter().count())
                .isEqualTo(1);
    }

    @Test
    void recordsErrorWhenElasticsearchFailureMustSurface() throws IOException {
        when(elasticsearchException.status()).thenReturn(400);
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenThrow(elasticsearchException);

        assertThatThrownBy(() -> gateway.search(new ProductSearchCriteria("通勤", null, 500)))
                .isSameAs(elasticsearchException);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "error").counter().count())
                .isEqualTo(1);
    }

    private Hit<Void> hit(String id) {
        return Hit.<Void>of(builder -> builder.index("product_v1").id(id));
    }
}
