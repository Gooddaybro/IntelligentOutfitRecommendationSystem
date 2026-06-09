package com.recommendation.intelligentoutfitrecommendationsystem.product.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Internal 商品事实接口。
 *
 * Python AI 推荐服务通过这些接口读取商品、SKU 和候选商品数据，避免维护独立商品目录。
 */
@RestController
@RequestMapping("/internal")
public class InternalProductController {

    private final ProductCatalogService productCatalogService;

    public InternalProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/products/search")
    public ApiResponse<List<ProductSearchItem>> searchProducts(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productCatalogService.searchProducts(keyword));
    }

    @GetMapping("/products/{spuId}")
    public ApiResponse<ProductDetail> getProductDetail(@PathVariable Long spuId) {
        return ApiResponse.ok(productCatalogService.getProductDetail(spuId));
    }

    @GetMapping("/skus/search")
    public ApiResponse<SkuSearchItem> findSku(
            @RequestParam Long spuId,
            @RequestParam String color,
            @RequestParam String size
    ) {
        return ApiResponse.ok(productCatalogService.findSku(spuId, color, size));
    }

    @GetMapping("/recommendation-candidates")
    public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(RecommendationCandidateQuery query) {
        return ApiResponse.ok(productCatalogService.findRecommendationCandidates(query));
    }
}
