package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 执行商品索引失败清理和成功后的历史保留策略。
 */
@Service
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class ProductSearchIndexLifecycleService {

    private final ProductSearchIndexAdminGateway gateway;
    private final ProductSearchIndexRetentionPolicy retentionPolicy;
    private final String indexPattern;
    private final String indexAlias;

    @Autowired
    public ProductSearchIndexLifecycleService(
            ProductSearchIndexAdminGateway gateway,
            ElasticsearchSearchProperties properties
    ) {
        this(
                gateway,
                new ProductSearchIndexRetentionPolicy(
                        properties.getIndexPrefix(), properties.getRetainedHistoryCount()),
                properties.getIndexAlias(),
                properties.getIndexPrefix() + "*"
        );
    }

    public ProductSearchIndexLifecycleService(
            ProductSearchIndexAdminGateway gateway,
            ProductSearchIndexRetentionPolicy retentionPolicy,
            String indexAlias
    ) {
        this(gateway, retentionPolicy, indexAlias, "product_*");
    }

    private ProductSearchIndexLifecycleService(
            ProductSearchIndexAdminGateway gateway,
            ProductSearchIndexRetentionPolicy retentionPolicy,
            String indexAlias,
            String indexPattern
    ) {
        this.gateway = gateway;
        this.retentionPolicy = retentionPolicy;
        this.indexAlias = indexAlias;
        this.indexPattern = indexPattern;
    }

    /**
     * 删除本次失败重建创建的物理索引；异常名称绝不下发到 ES。
     */
    public void deleteFailedIndex(String indexName) {
        if (retentionPolicy.isManagedIndex(indexName)) {
            gateway.deleteIndex(indexName);
        }
    }

    /**
     * 在别名切换成功后删除超出回滚窗口的历史索引。
     */
    public void pruneHistory() {
        var indices = gateway.listIndices(indexPattern);
        var aliasTargets = gateway.findAliasTargets(indexAlias);
        retentionPolicy.indicesToDelete(indices, aliasTargets).forEach(gateway::deleteIndex);
    }
}
