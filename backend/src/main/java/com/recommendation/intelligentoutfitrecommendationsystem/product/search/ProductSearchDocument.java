package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.time.Instant;
import java.util.List;

/**
 * 写入 Elasticsearch 的商品搜索文档；字段名与严格 Mapping 保持一致。
 */
public record ProductSearchDocument(
        Long spuId,
        String spuCode,
        String name,
        String description,
        String category,
        String fitType,
        List<String> materials,
        List<String> styles,
        List<String> scenes,
        List<String> seasons,
        String status,
        Instant updatedAt
) {
}
