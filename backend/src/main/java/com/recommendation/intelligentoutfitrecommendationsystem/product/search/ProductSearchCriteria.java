package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

/**
 * 跨搜索实现传递的标准化商品查询条件。
 *
 * <p>条件在进入网关前统一去除首尾空白，避免 Elasticsearch 与 MySQL
 * 对空字符串产生不同解释；数量上限则防止搜索结果被无界加载到内存。</p>
 *
 * @param keyword  商品关键词；没有关键词时为 {@code null}
 * @param category 精确分类名称；没有分类筛选时为 {@code null}
 * @param limit    最多返回的 SPU 数量
 */
public record ProductSearchCriteria(String keyword, String category, int limit) {

    private static final int MAX_LIMIT = 500;

    public ProductSearchCriteria {
        keyword = normalize(keyword);
        category = normalize(category);
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("商品搜索数量必须在 1 到 " + MAX_LIMIT + " 之间");
        }
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
