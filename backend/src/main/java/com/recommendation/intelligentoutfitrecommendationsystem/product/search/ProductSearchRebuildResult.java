package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

/**
 * 商品搜索索引全量重建的可审计结果。
 *
 * @param indexName 新建并切换到的物理索引
 * @param documentCount 已验证的文档数量
 * @param alias 当前查询别名
 */
public record ProductSearchRebuildResult(String indexName, long documentCount, String alias) {
}
