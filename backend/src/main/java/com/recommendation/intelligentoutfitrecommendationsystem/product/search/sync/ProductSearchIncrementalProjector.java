package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ElasticsearchSearchProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRow;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchDocument;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;

/**
 * 以 MySQL 当前事实重建单个商品文档，而不是信任可能过期的消息快照。
 */
@Component
@ConditionalOnExpression("${app.product-search-sync.enabled:false} and ${app.elasticsearch.enabled:false}")
public class ProductSearchIncrementalProjector {
    private final ElasticsearchClient client;
    private final ProductMapper productMapper;
    private final ElasticsearchSearchProperties properties;
    private final Clock clock;

    public ProductSearchIncrementalProjector(
            ElasticsearchClient client,
            ProductMapper productMapper,
            ElasticsearchSearchProperties properties,
            Clock clock) {
        this.client = client;
        this.productMapper = productMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public void project(Long spuId) {
        ProductSearchIndexRow row = productMapper.findSearchIndexRowBySpuId(spuId);
        try {
            if (row == null) {
                client.delete(DeleteRequest.of(request -> request
                        .index(properties.getIndexAlias()).id(String.valueOf(spuId))));
                return;
            }
            IndexRequest<ProductSearchDocument> request = IndexRequest.of(builder -> builder
                    .index(properties.getIndexAlias())
                    .id(String.valueOf(spuId))
                    .document(row.toDocument(clock.instant())));
            client.index(request);
        } catch (IOException exception) {
            throw new ProductSearchUnavailableException("商品搜索增量投影失败", exception);
        }
    }
}
