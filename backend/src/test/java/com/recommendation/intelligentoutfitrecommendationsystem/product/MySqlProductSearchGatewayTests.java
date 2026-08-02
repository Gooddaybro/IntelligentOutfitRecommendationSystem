package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.MySqlProductSearchGateway;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchCriteria;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MySqlProductSearchGatewayTests {

    @Mock
    private ProductMapper productMapper;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

    @Test
    void recordsSuccessfulMysqlSearchLatency() {
        MySqlProductSearchGateway gateway = new MySqlProductSearchGateway(productMapper, metrics);
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(productMapper.searchProductIds("通勤", null, 500)).thenReturn(List.of(1001L));

        assertThat(gateway.search(criteria)).containsExactly(1001L);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "mysql", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.engine.duration")
                .tags("engine", "mysql", "outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsMysqlSearchErrorAndRethrows() {
        MySqlProductSearchGateway gateway = new MySqlProductSearchGateway(productMapper, metrics);
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(productMapper.searchProductIds("通勤", null, 500))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> gateway.search(criteria))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");
        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "mysql", "outcome", "error").counter().count())
                .isEqualTo(1);
    }
}
