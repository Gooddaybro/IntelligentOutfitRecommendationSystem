package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.util.List;

/**
 * 商品搜索引擎的最小业务边界。
 *
 * <p>网关只返回按相关度排列的 SPU ID；价格、上下架状态等实时事实仍由
 * MySQL 补齐，避免把 Elasticsearch 误用成商品事实库。</p>
 */
public interface ProductSearchGateway {

    /**
     * 按查询条件返回有序且不重复的 SPU ID。
     *
     * @param criteria 已标准化的查询条件
     * @return 搜索引擎给出的相关度顺序
     */
    List<Long> search(ProductSearchCriteria criteria);
}
