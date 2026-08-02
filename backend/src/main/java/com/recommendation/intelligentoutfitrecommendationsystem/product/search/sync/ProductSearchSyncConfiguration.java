package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 根据总开关选择真实 Outbox Recorder 或无操作实现。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProductSearchSyncProperties.class)
public class ProductSearchSyncConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "app.product-search-sync", name = "enabled", havingValue = "true")
    ProductSearchChangeRecorder outboxProductSearchChangeRecorder(
            ProductSearchOutboxMapper mapper, Clock clock) {
        ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        return new OutboxProductSearchChangeRecorder(mapper, objectMapper, clock);
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.product-search-sync", name = "enabled",
            havingValue = "false", matchIfMissing = true)
    ProductSearchChangeRecorder noOpProductSearchChangeRecorder() {
        return new NoOpProductSearchChangeRecorder();
    }
}
