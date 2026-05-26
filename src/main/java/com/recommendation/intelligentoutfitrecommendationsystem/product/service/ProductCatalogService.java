package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductCatalogService {

    private final ProductMapper productMapper;

    public ProductCatalogService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        return productMapper.searchProducts(keyword);
    }

    public ProductDetail getProductDetail(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        ProductDetail detail = productMapper.findProductDetailBase(spuId);
        if (detail == null) {
            throw new ResourceNotFoundException("product not found: " + spuId);
        }
        detail.setMaterials(productMapper.findMaterials(spuId));
        detail.setSeasons(productMapper.findSeasons(spuId));
        detail.setStyleTags(productMapper.findStyleTags(spuId));
        detail.setAttributes(toAttributesMap(productMapper.findAttributes(spuId)));
        return detail;
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
        SkuSearchItem sku = productMapper.findSku(spuId, color.trim(), size.trim().toUpperCase());
        if (sku == null) {
            throw new ResourceNotFoundException("sku not found");
        }
        return sku;
    }

    public List<RecommendationCandidate> findRecommendationCandidates(RecommendationCandidateQuery query) {
        if (query.getBudgetMax() != null && query.getBudgetMax() < 0) {
            throw new BadRequestException("budgetMax must not be negative");
        }
        return productMapper.findRecommendationCandidates(query);
    }

    private Map<String, String> toAttributesMap(List<ProductAttributeItem> attributes) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ProductAttributeItem attribute : attributes) {
            result.put(attribute.getAttrName(), attribute.getAttrValue());
        }
        return result;
    }
}
