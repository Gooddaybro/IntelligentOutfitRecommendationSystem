package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import co.elastic.clients.elasticsearch.indices.IndexState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * 使用官方 Java Client 读取商品索引元数据并执行精确名称删除。
 */
@Component
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchProductSearchIndexAdminGateway implements ProductSearchIndexAdminGateway {

    private final ElasticsearchClient client;

    public ElasticsearchProductSearchIndexAdminGateway(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public List<ProductSearchIndexDescriptor> listIndices(String indexPattern) {
        try {
            GetIndexResponse response = client.indices().get(request -> request.index(indexPattern));
            return response.indices().entrySet().stream()
                    .map(entry -> new ProductSearchIndexDescriptor(
                            entry.getKey(), Instant.ofEpochMilli(readCreationDate(entry.getValue()))))
                    .toList();
        } catch (ElasticsearchException exception) {
            if (exception.status() == 404) {
                return List.of();
            }
            throw exception;
        } catch (IOException exception) {
            throw new ProductSearchUnavailableException("读取商品索引列表失败", exception);
        }
    }

    @Override
    public Set<String> findAliasTargets(String alias) {
        try {
            return Set.copyOf(client.indices().getAlias(request -> request.name(alias)).aliases().keySet());
        } catch (ElasticsearchException exception) {
            if (exception.status() == 404) {
                return Set.of();
            }
            throw exception;
        } catch (IOException exception) {
            throw new ProductSearchUnavailableException("读取商品索引别名失败", exception);
        }
    }

    @Override
    public void deleteIndex(String indexName) {
        try {
            // 只接受上层校验后的精确索引名；ignoreUnavailable 让重复清理保持幂等。
            client.indices().delete(request -> request.index(indexName).ignoreUnavailable(true));
        } catch (IOException exception) {
            throw new ProductSearchUnavailableException("删除商品历史索引失败: " + indexName, exception);
        }
    }

    private long readCreationDate(IndexState state) {
        IndexSettings settings = state.settings();
        Long creationDate = settings.index() == null
                ? settings.creationDate()
                : settings.index().creationDate();
        if (creationDate == null) {
            throw new IllegalStateException("Elasticsearch 未返回商品索引创建时间");
        }
        return creationDate;
    }
}
