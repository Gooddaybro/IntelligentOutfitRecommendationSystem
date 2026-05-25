package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.repository.ProductQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCatalogService {

    private final ProductQueryRepository productQueryRepository;

    public ProductCatalogService(ProductQueryRepository productQueryRepository) {
        this.productQueryRepository = productQueryRepository;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        return productQueryRepository.searchProducts(keyword);
    }

    public ProductDetail getProductDetail(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        return productQueryRepository.findProductDetail(spuId)
                .orElseThrow(() -> new ResourceNotFoundException("product not found: " + spuId));
    }

    public SkuSearchItem findSku(Long spuId, String color, String size) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        if (color == null || color.isBlank()) {
            throw new BadRequestException("color must not be blank");
        }
        if (size == null || size.isBlank()) {
            throw new BadRequestException("size must not be blank");
        }
        return productQueryRepository.findSku(spuId, color.trim(), size.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("sku not found"));
    }

    public List<RecommendationCandidate> findRecommendationCandidates(
            String category,
            String style,
            String season,
            String material,
            String fit,
            Integer budgetMax
    ) {
        if (budgetMax != null && budgetMax < 0) {
            throw new BadRequestException("budgetMax must not be negative");
        }
        return productQueryRepository.findRecommendationCandidates(category, style, season, material, fit, budgetMax);
    }
}
