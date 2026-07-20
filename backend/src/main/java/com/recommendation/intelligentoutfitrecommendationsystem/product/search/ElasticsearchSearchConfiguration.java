package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建官方 Elasticsearch Java Client，并把连接生命周期交给 Spring 管理。
 *
 * <p>客户端只在显式开启商品搜索副本时存在；Elasticsearch 不属于应用启动和 readiness 的硬依赖。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ElasticsearchSearchProperties.class)
public class ElasticsearchSearchConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
    RestClient elasticsearchRestClient(ElasticsearchSearchProperties properties) {
        HttpHost[] hosts = properties.getUris().stream().map(HttpHost::create).toArray(HttpHost[]::new);
        return RestClient.builder(hosts)
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(properties.getSocketTimeoutMs()))
                .build();
    }

    @Bean(destroyMethod = "")
    @ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
    ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
    ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
