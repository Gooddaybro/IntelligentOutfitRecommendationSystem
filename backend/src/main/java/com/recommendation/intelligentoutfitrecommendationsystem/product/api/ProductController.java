package com.recommendation.intelligentoutfitrecommendationsystem.product.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商品与推荐候选模块
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;
    private final RecommendationCandidateQueryService recommendationCandidateQueryService;

    public ProductController(
            ProductCatalogService productCatalogService,
            RecommendationCandidateQueryService recommendationCandidateQueryService
    ) {
        this.productCatalogService = productCatalogService;
        this.recommendationCandidateQueryService = recommendationCandidateQueryService;
    }

    /**
     * 商品搜索
     *
     * @param keyword
     * @return
     */
    @GetMapping
    public ApiResponse<List<ProductSearchItem>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category
    ) {
        return ApiResponse.ok(productCatalogService.searchProducts(keyword, category));
    }

    @GetMapping("/recommendation-candidates")
    public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(RecommendationCandidateQuery query) {
        return ApiResponse.ok(recommendationCandidateQueryService.findCandidates(query));
    }

    @GetMapping("/{spuId}")
    public ApiResponse<ProductDetail> getProductDetail(@PathVariable Long spuId) {
        return ApiResponse.ok(productCatalogService.getProductDetail(spuId));
    }
}
