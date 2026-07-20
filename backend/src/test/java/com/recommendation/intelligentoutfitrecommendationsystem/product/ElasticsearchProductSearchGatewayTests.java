package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
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

    private ElasticsearchProductSearchGateway gateway;

    @BeforeEach
    void setUp() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexAlias("product_current");
        gateway = new ElasticsearchProductSearchGateway(client, properties);
    }

    @Test
    void searchesAliasWithWeightedFieldsAndReturnsHitOrder() throws IOException {
        when(client.search(org.mockito.ArgumentMatchers.any(SearchRequest.class),
                org.mockito.ArgumentMatchers.<Class<Void>>any())).thenReturn(response);
        when(response.hits()).thenReturn(hits);
        when(hits.hits()).thenReturn(List.of(hit("1106"), hit("1124")));

        assertThat(gateway.search(new ProductSearchCriteria("黑色硬朗外套", "外套", 20)))
                .containsExactly(1106L, 1124L);

        ArgumentCaptor<SearchRequest> requestCaptor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(client).search(requestCaptor.capture(), org.mockito.ArgumentMatchers.<Class<Void>>any());
        SearchRequest request = requestCaptor.getValue();
        assertThat(request.index()).containsExactly("product_current");
        assertThat(request.size()).isEqualTo(20);
        assertThat(request.query().bool().must().getFirst().multiMatch().fields())
                .containsExactly("name^5", "styles^3", "category^2", "description");
        assertThat(request.query().bool().filter()).hasSize(2);
    }

    @Test
    void translatesTransportFailureIntoFallbackSignal() throws IOException {
        when(client.search(org.mockito.ArgumentMatchers.any(SearchRequest.class),
                org.mockito.ArgumentMatchers.<Class<Void>>any())).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> gateway.search(new ProductSearchCriteria("外套", null, 20)))
                .isInstanceOf(com.recommendation.intelligentoutfitrecommendationsystem.product.search
                        .ProductSearchUnavailableException.class)
                .hasMessageContaining("Elasticsearch");
    }

    private Hit<Void> hit(String id) {
        return Hit.<Void>of(builder -> builder.index("product_v1").id(id));
    }
}
