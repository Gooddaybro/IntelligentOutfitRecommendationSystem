package com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache;

import org.springframework.stereotype.Component;

/**
 * Builds unambiguous product search cache keys whose version namespace makes stale generations unreachable.
 */
@Component
public class ProductSearchCacheKeyFactory {

    /**
     * Escapes Redis key delimiters in caller-normalized query parts while isolating entries by cache generation.
     *
     * @param version positive cache generation
     * @param normalizedKeyword caller-normalized keyword
     * @param normalizedCategory caller-normalized category
     * @return the versioned Redis cache key
     * @throws IllegalArgumentException when {@code version} is not positive
     */
    public String create(long version, String normalizedKeyword, String normalizedCategory) {
        if (version <= 0) {
            throw new IllegalArgumentException("product search cache version must be positive");
        }
        return "product:search:v" + version + ":"
                + escapeQueryPart(normalizedKeyword) + ":"
                + escapeQueryPart(normalizedCategory);
    }

    private String escapeQueryPart(String value) {
        return value.replace("%", "%25").replace(":", "%3A");
    }
}
