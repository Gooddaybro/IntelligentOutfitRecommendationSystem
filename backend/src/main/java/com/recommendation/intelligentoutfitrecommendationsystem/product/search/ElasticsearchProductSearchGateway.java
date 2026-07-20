package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 使用官方 Java Client 查询 Elasticsearch 商品索引别名。
 */
@Component
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class ElasticsearchProductSearchGateway implements ProductSearchGateway {

    private final ElasticsearchClient client;
    private final ElasticsearchSearchProperties properties;

    public ElasticsearchProductSearchGateway(
            ElasticsearchClient client,
            ElasticsearchSearchProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public List<Long> search(ProductSearchCriteria criteria) {
        SearchRequest request = buildRequest(criteria);
        try {
            SearchResponse<Void> response = client.search(request, Void.class);
            return response.hits().hits().stream().map(hit -> parseSpuId(hit.id())).toList();
        } catch (IOException exception) {
            throw new ProductSearchUnavailableException("Elasticsearch 连接不可用", exception);
        } catch (ElasticsearchException exception) {
            // 节点故障和索引尚未创建允许降级；400 类查询错误必须暴露，避免掩盖映射缺陷。
            if (exception.status() == 404 || exception.status() >= 500) {
                throw new ProductSearchUnavailableException("Elasticsearch 查询暂时不可用", exception);
            }
            throw exception;
        }
    }

    private SearchRequest buildRequest(ProductSearchCriteria criteria) {
        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(query -> query.term(term -> term.field("status").value("on_sale"))));
        if (criteria.category() != null) {
            filters.add(Query.of(query -> query.term(term -> term.field("category").value(criteria.category()))));
        }

        Query query;
        if (criteria.keyword() == null) {
            query = Query.of(builder -> builder.bool(bool -> bool
                    .must(must -> must.matchAll(matchAll -> matchAll))
                    .filter(filters)));
        } else {
            query = Query.of(builder -> builder.bool(bool -> bool
                    .must(must -> must.multiMatch(multiMatch -> multiMatch
                            .query(criteria.keyword())
                            .fields(
                                    "name.smartcn^5",
                                    "styles.search^3",
                                    "category.search^2",
                                    "scenes.search^2",
                                    "materials.search^1.5",
                                    "description.smartcn")))
                    .filter(filters)));
        }

        return SearchRequest.of(builder -> builder
                .index(properties.getIndexAlias())
                .size(criteria.limit())
                .source(source -> source.fetch(false))
                .query(query)
                .sort(sort -> sort.score(score -> score.order(SortOrder.Desc)))
                .sort(sort -> sort.field(field -> field.field("spuId").order(SortOrder.Asc))));
    }

    private Long parseSpuId(String hitId) {
        try {
            return Long.valueOf(hitId);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("商品索引文档 ID 必须是数字 SPU ID: " + hitId, exception);
        }
    }
}
