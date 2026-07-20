package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * MySQL 全量索引查询的一行聚合结果。
 *
 * <p>SQL 用逗号聚合多值标签以避免逐商品查询；在 Java 中拆分和去重后再写入 ES 数组。</p>
 */
public record ProductSearchIndexRow(
        Long spuId,
        String spuCode,
        String name,
        String description,
        String category,
        String fitType,
        String materials,
        String styles,
        String scenes,
        String seasons,
        String status
) {

    /**
     * 转换成搜索文档，并以本次重建时间标记同一批快照。
     */
    public ProductSearchDocument toDocument(Instant rebuiltAt) {
        return new ProductSearchDocument(
                spuId, spuCode, name, description, category, fitType,
                split(materials), split(styles), split(scenes), split(seasons), status, rebuiltAt);
    }

    private List<String> split(String values) {
        if (values == null || values.isBlank()) {
            return List.of();
        }
        return Arrays.stream(values.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
