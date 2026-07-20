package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * 商品搜索增量同步的开关、批量、租约和 RabbitMQ 确认参数。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.product-search-sync")
public class ProductSearchSyncProperties {
    private boolean enabled;
    private boolean publisherEnabled;
    private boolean listenerEnabled;
    @Min(1)
    private int publisherBatchSize = 20;
    private Duration publisherFixedDelay = Duration.ofSeconds(1);
    private Duration claimDuration = Duration.ofSeconds(30);
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private Duration retry10s = Duration.ofSeconds(10);
    private Duration retry60s = Duration.ofSeconds(60);
    private Duration retry300s = Duration.ofSeconds(300);
}
