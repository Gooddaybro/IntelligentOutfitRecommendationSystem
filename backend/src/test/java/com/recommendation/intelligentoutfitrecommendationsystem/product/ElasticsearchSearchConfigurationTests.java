package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ElasticsearchSearchConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ElasticsearchSearchConfiguration.class);

    @Test
    void doesNotCreateClientWhenSearchIsDisabled() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(ElasticsearchClient.class));
    }

    @Test
    void createsOfficialClientWhenSearchIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.elasticsearch.enabled=true",
                        "app.elasticsearch.uris=http://localhost:9200"
                )
                .run(context -> assertThat(context).hasSingleBean(ElasticsearchClient.class));
    }
}
