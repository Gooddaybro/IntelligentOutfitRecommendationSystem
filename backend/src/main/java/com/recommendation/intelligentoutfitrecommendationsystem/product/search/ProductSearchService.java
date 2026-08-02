package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排商品搜索、故障降级和 MySQL 实时数据补齐。
 */
public class ProductSearchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchService.class);

    private final ProductSearchGateway primaryGateway;
    private final ProductSearchGateway fallbackGateway;
    private final ProductMapper productMapper;
    private final int searchLimit;
    private final ApplicationMetrics metrics;

    public ProductSearchService(
            ProductSearchGateway primaryGateway,
            ProductSearchGateway fallbackGateway,
            ProductMapper productMapper,
            int searchLimit,
            ApplicationMetrics metrics
    ) {
        this.primaryGateway = primaryGateway;
        this.fallbackGateway = fallbackGateway;
        this.productMapper = productMapper;
        this.searchLimit = searchLimit;
        this.metrics = metrics;
    }

    /**
     * 搜索商品，并以 MySQL 中仍然有效的商品事实生成返回结果。
     *
     * @param keyword  商品关键词
     * @param category 分类筛选
     * @return 保持搜索引擎相关度顺序的商品摘要
     */
    public List<ProductSearchItem> search(String keyword, String category) {
        ProductSearchCriteria criteria = new ProductSearchCriteria(keyword, category, searchLimit);
        List<Long> orderedSpuIds;
        try {
            orderedSpuIds = primaryGateway.search(criteria);
        } catch (ProductSearchUnavailableException exception) {
            LOGGER.warn("主商品搜索不可用，回退到 MySQL 搜索: {}", exception.getMessage());
            metrics.recordProductSearchFallback("unavailable");
            orderedSpuIds = fallbackGateway.search(criteria);
        }

        if (orderedSpuIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<ProductSearchItem> currentItems = productMapper.findSearchItemsBySpuIds(orderedSpuIds);
        Map<Long, ProductSearchItem> itemsById = new LinkedHashMap<>();
        currentItems.forEach(item -> itemsById.put(item.getSpuId(), item));

        // SQL 的 IN 查询不会保留 ES 相关度顺序，因此必须按搜索命中 ID 在内存中重新装配。
        return orderedSpuIds.stream()
                .distinct()
                .map(itemsById::get)
                .filter(item -> item != null)
                .toList();
    }
}
