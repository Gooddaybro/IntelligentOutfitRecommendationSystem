package com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchCacheKeyFactoryTests {

    private final ProductSearchCacheKeyFactory factory = new ProductSearchCacheKeyFactory();

    @Test
    void createsDifferentNamespacesForDifferentVersions() {
        assertThat(factory.create(7, "coat", "外套"))
                .isEqualTo("product:search:v7:coat:外套");
        assertThat(factory.create(8, "coat", "外套"))
                .isEqualTo("product:search:v8:coat:外套");
    }

    @Test
    void rejectsNonPositiveVersion() {
        assertThatThrownBy(() -> factory.create(0, "coat", "外套"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("product search cache version must be positive");
    }
}
