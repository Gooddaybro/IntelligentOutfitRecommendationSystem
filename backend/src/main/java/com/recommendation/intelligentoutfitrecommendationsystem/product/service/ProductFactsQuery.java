package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;

/**
 * Public read boundary for other modules that need Java-owned product and SKU facts.
 * Hides catalog cache and mapper implementation while exposing no mutation operations.
 */
public interface ProductFactsQuery {
    /** Reads a product's descriptive facts and price range, not a particular SKU's price. */
    ProductDetail getProductDetail(Long spuId);

    /** Resolves a concrete SKU using the catalog's color and size normalization rules. */
    SkuSearchItem findSku(Long spuId, String color, String size);
}
