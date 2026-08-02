package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.util.List;
import java.util.Set;

/**
 * 商品物理索引生命周期所需的最小 Elasticsearch 管理边界。
 */
public interface ProductSearchIndexAdminGateway {

    List<ProductSearchIndexDescriptor> listIndices(String indexPattern);

    Set<String> findAliasTargets(String alias);

    void deleteIndex(String indexName);
}
