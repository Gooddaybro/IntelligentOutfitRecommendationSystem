package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
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

/**
 * 商品目录服务，负责商品搜索、详情装配、SKU 查询和推荐候选商品池筛选。
 */
@Service
public class ProductCatalogService {

    private final ProductMapper productMapper;
    private final RedisCacheService redisCacheService;
    private final CacheTtlProperties cacheTtlProperties;

    public ProductCatalogService(
            ProductMapper productMapper,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties
    ) {
        this.productMapper = productMapper;
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        return productMapper.searchProducts(keyword);
    }

    public ProductDetail getProductDetail(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        String cacheKey = CacheKeyConstants.productDetail(spuId);
        var cachedDetail = redisCacheService.getValue(cacheKey, ProductDetail.class);
        if (cachedDetail.isPresent()) {
            return cachedDetail.get();
        }
        ProductDetail detail = productMapper.findProductDetailBase(spuId);
        if (detail == null) {
            throw new ResourceNotFoundException("product not found: " + spuId);
        }
        // 多值属性保持为轻量查询后在 Service 层装配，避免 XML resultMap 过度嵌套。
        // 这里返回给商城前端和 Python AI 服务的都是完整商品事实数据。
        detail.setMaterials(productMapper.findMaterials(spuId));
        detail.setSeasons(productMapper.findSeasons(spuId));
        detail.setStyleTags(productMapper.findStyleTags(spuId));
        detail.setAttributes(toAttributesMap(productMapper.findAttributes(spuId)));
        redisCacheService.setValue(cacheKey, detail, cacheTtlProperties.productDetailTtl());
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
        // 尺码统一转成标准码，避免 Python AI 服务传入 " l " 时查不到 SKU。
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
        // Java 只负责提供可靠候选商品池，最终自然语言解释和个性化排序交给 Python AI 服务。
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
