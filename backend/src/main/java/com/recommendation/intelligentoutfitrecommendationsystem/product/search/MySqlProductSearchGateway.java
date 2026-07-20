package com.recommendation.intelligentoutfitrecommendationsystem.product.search;

import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 使用现有 MySQL LIKE 查询提供商品搜索及 Elasticsearch 故障回退。
 */
@Component
public class MySqlProductSearchGateway implements ProductSearchGateway {

    private final ProductMapper productMapper;

    public MySqlProductSearchGateway(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    @Override
    public List<Long> search(ProductSearchCriteria criteria) {
        return productMapper.searchProductIds(criteria.keyword(), criteria.category(), criteria.limit());
    }
}
