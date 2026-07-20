package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 根据功能开关选择商品主搜索引擎，同时始终保留 MySQL 降级通道。
 */
@Configuration(proxyBeanMethods = false)
public class ProductSearchServiceConfiguration {

    @Bean
    ProductSearchService productSearchService(
            MySqlProductSearchGateway mysqlGateway,
            ObjectProvider<ElasticsearchProductSearchGateway> elasticsearchGateway,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties
    ) {
        ElasticsearchProductSearchGateway availableGateway = elasticsearchGateway.getIfAvailable();
        ProductSearchGateway primaryGateway = availableGateway == null ? mysqlGateway : availableGateway;
        return new ProductSearchService(primaryGateway, mysqlGateway, productMapper, properties.getSearchLimit());
    }
}
