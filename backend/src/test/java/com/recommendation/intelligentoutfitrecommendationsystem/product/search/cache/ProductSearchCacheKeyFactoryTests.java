package com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductSearchCacheKeyFactoryTests {

    private final ProductSearchCacheKeyFactory factory = new ProductSearchCacheKeyFactory();

    @Test
    void createsDifferentNamespacesForDifferentVersions() {
        assertThat(factory.create(7, "coat", "外套"))
                .isEqualTo("product:search-versioned:v7:coat:外套");
        assertThat(factory.create(8, "coat", "外套"))
                .isEqualTo("product:search-versioned:v8:coat:外套");
    }

    @Test
    void rejectsNonPositiveVersion() {
        assertThatThrownBy(() -> factory.create(0, "coat", "外套"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("product search cache version must be positive");
    }

    @Test
    void escapesSeparatorsSoDifferentQueryPartsCannotShareAKey() {
        assertThat(factory.create(7, "a:b", "c"))
                .isEqualTo("product:search-versioned:v7:a%3Ab:c")
                .isNotEqualTo(factory.create(7, "a", "b:c"));
        assertThat(factory.create(7, "a", "b:c"))
                .isEqualTo("product:search-versioned:v7:a:b%3Ac");
    }

    @Test
    void escapesPercentBeforeSeparatorToAvoidEncodedTextCollisions() {
        assertThat(factory.create(7, "a%3Ab", "c"))
                .isEqualTo("product:search-versioned:v7:a%253Ab:c")
                .isNotEqualTo(factory.create(7, "a:b", "c"));
    }

    @Test
    void versionedNamespaceCannotCollideWithLegacySearchKey() {
        assertThat(factory.create(7, "a", "b"))
                .isNotEqualTo("product:search:v7:a:b");
    }
}
