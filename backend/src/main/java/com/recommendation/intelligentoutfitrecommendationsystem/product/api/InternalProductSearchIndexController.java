package com.recommendation.intelligentoutfitrecommendationsystem.product.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchRebuildResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 仅在 Elasticsearch 功能开启时暴露商品索引运维入口。
 */
@RestController
@RequestMapping("/internal/search/products")
@ConditionalOnProperty(prefix = "app.elasticsearch", name = "enabled", havingValue = "true")
public class InternalProductSearchIndexController {

    private final ProductSearchIndexService indexService;

    public InternalProductSearchIndexController(ProductSearchIndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * 从 MySQL 全量创建新索引并切换查询别名。
     */
    @PostMapping("/rebuild")
    public ApiResponse<ProductSearchRebuildResult> rebuild() {
        return ApiResponse.ok(indexService.rebuild());
    }
}
