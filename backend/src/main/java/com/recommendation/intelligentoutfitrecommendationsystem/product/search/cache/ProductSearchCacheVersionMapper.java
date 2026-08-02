package com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache;

import org.apache.ibatis.annotations.Mapper;

/**
 * Persists the single global generation used to invalidate product search cache entries across instances.
 */
@Mapper
public interface ProductSearchCacheVersionMapper {
    Long findCurrentVersion();

    int incrementVersion();
}
