package com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache;

import org.springframework.stereotype.Service;

/**
 * Enforces the invariant that the shared product search cache generation is a single positive database value.
 */
@Service
public class ProductSearchCacheVersionService {
    private final ProductSearchCacheVersionMapper versionMapper;

    public ProductSearchCacheVersionService(ProductSearchCacheVersionMapper versionMapper) {
        this.versionMapper = versionMapper;
    }

    /**
     * Returns the durable cache generation and fails fast if the singleton state has been corrupted or removed.
     *
     * @return the positive global cache generation
     * @throws IllegalStateException when the singleton state is missing or invalid
     */
    public long currentVersion() {
        Long version = versionMapper.findCurrentVersion();
        if (version == null || version <= 0) {
            throw new IllegalStateException("Product search cache version is missing or invalid");
        }
        return version;
    }

    /**
     * Atomically advances the durable generation while preserving the singleton-row invariant.
     *
     * @throws IllegalStateException when the singleton state row cannot be updated exactly once
     */
    public void incrementVersion() {
        int updatedRows = versionMapper.incrementVersion();
        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Product search cache version update affected " + updatedRows + " rows instead of 1");
        }
    }
}
