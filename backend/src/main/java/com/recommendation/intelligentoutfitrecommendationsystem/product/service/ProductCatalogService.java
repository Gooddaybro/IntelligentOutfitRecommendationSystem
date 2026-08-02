package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheKeyFactory;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheVersionService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 商品目录服务，负责商品搜索、详情装配和 SKU 查询。
 */
@Service
public class ProductCatalogService {

    private final ProductMapper productMapper;
    private final ProductSearchService productSearchService;
    private final RedisCacheService redisCacheService;
    private final CacheTtlProperties cacheTtlProperties;
    private final ProductSearchCacheVersionService productSearchCacheVersionService;
    private final ProductSearchCacheKeyFactory productSearchCacheKeyFactory;

    public ProductCatalogService(
            ProductMapper productMapper,
            ProductSearchService productSearchService,
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties,
            ProductSearchCacheVersionService productSearchCacheVersionService,
            ProductSearchCacheKeyFactory productSearchCacheKeyFactory
    ) {
        this.productMapper = productMapper;
        this.productSearchService = productSearchService;
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
        this.productSearchCacheVersionService = productSearchCacheVersionService;
        this.productSearchCacheKeyFactory = productSearchCacheKeyFactory;
    }

    /**
     * 商品搜索
     *
     * @param keyword
     * @return
     */
    public List<ProductSearchItem> searchProducts(String keyword, String category) {
        String normalizedKeyword = normalizeQueryPart(keyword);
        String mapperKeyword = keyword == null ? null : keyword.trim();
        String normalizedCategory = normalizeQueryPart(category);
        String mapperCategory = category == null ? null : category.trim();

        long cacheVersion = productSearchCacheVersionService.currentVersion();
        String cacheKey = productSearchCacheKeyFactory.create(
                cacheVersion, normalizedKeyword, normalizedCategory);
        var cachedProducts = redisCacheService.getList(cacheKey, ProductSearchItem.class);
        if (cachedProducts.isPresent()) {
            return cachedProducts.get();
        }
        List<ProductSearchItem> products = productSearchService.search(mapperKeyword, mapperCategory);
        redisCacheService.setValue(cacheKey, products, cacheTtlProperties.productSearchTtl());
        return products;
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

    private Map<String, String> toAttributesMap(List<ProductAttributeItem> attributes) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ProductAttributeItem attribute : attributes) {
            result.put(attribute.getAttrName(), attribute.getAttrValue());
        }
        return result;
    }

    private String normalizeQueryPart(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

}
