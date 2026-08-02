package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.time.Instant;

/**
 * Elasticsearch 物理商品索引的名称和创建时间快照。
 *
 * @param name       物理索引名称
 * @param createdAt  Elasticsearch 记录的创建时间
 */
public record ProductSearchIndexDescriptor(String name, Instant createdAt) {
}
