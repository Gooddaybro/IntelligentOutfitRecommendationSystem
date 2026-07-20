package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 计算可以安全删除的商品历史索引，不执行任何 Elasticsearch 操作。
 *
 * <p>前缀校验之外再要求 14 位时间戳后缀，避免错误配置前缀时把实验索引或其他业务索引
 * 纳入删除范围；当前别名目标则独立保护，不依赖其创建时间。</p>
 */
public class ProductSearchIndexRetentionPolicy {

    private final Pattern managedNamePattern;
    private final int retainedHistoryCount;

    public ProductSearchIndexRetentionPolicy(String indexPrefix, int retainedHistoryCount) {
        if (retainedHistoryCount < 0) {
            throw new IllegalArgumentException("历史索引保留数量不能为负数");
        }
        this.managedNamePattern = Pattern.compile(Pattern.quote(indexPrefix) + "\\d{14}");
        this.retainedHistoryCount = retainedHistoryCount;
    }

    /**
     * 返回超出保留窗口的索引名称，顺序从较新到较旧。
     *
     * @param indices            Elasticsearch 中的索引快照
     * @param currentAliasTargets 当前查询别名指向的索引集合
     * @return 允许删除的受管历史索引
     */
    public List<String> indicesToDelete(
            List<ProductSearchIndexDescriptor> indices,
            Set<String> currentAliasTargets
    ) {
        return indices.stream()
                .filter(index -> managedNamePattern.matcher(index.name()).matches())
                .filter(index -> !currentAliasTargets.contains(index.name()))
                .sorted(Comparator.comparing(ProductSearchIndexDescriptor::createdAt).reversed())
                .skip(retainedHistoryCount)
                .map(ProductSearchIndexDescriptor::name)
                .toList();
    }

    /**
     * 判断索引是否属于该生命周期策略，用于实际删除前执行第二次安全校验。
     */
    public boolean isManagedIndex(String indexName) {
        return managedNamePattern.matcher(indexName).matches();
    }
}
