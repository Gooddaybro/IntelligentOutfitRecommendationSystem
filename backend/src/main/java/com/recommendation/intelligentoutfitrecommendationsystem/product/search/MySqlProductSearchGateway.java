package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 使用现有 MySQL LIKE 查询提供商品搜索及 Elasticsearch 故障回退。
 */
@Component
public class MySqlProductSearchGateway implements ProductSearchGateway {

    private final ProductMapper productMapper;
    private final ApplicationMetrics metrics;

    public MySqlProductSearchGateway(ProductMapper productMapper, ApplicationMetrics metrics) {
        this.productMapper = productMapper;
        this.metrics = metrics;
    }

    @Override
    public List<Long> search(ProductSearchCriteria criteria) {
        long startedNanos = System.nanoTime();
        try {
            List<Long> result = productMapper.searchProductIds(
                    criteria.keyword(), criteria.category(), criteria.limit());
            metrics.recordProductSearchEngine("mysql", "success", elapsed(startedNanos));
            return result;
        } catch (RuntimeException exception) {
            metrics.recordProductSearchEngine("mysql", "error", elapsed(startedNanos));
            throw exception;
        }
    }

    private Duration elapsed(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
