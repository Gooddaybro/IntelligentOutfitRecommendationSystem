package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexDescriptor;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchIndexRetentionPolicyTests {

    private final ProductSearchIndexRetentionPolicy policy =
            new ProductSearchIndexRetentionPolicy("product_", 2);

    @Test
    void protectsCurrentIndexAndKeepsTwoNewestHistoricalIndices() {
        List<ProductSearchIndexDescriptor> indices = List.of(
                index("product_20260720140000", 4),
                index("product_20260720130000", 3),
                index("product_20260720120000", 2),
                index("product_20260720110000", 1)
        );

        assertThat(policy.indicesToDelete(indices, Set.of("product_20260720140000")))
                .containsExactly("product_20260720110000");
    }

    @Test
    void protectsAliasTargetEvenWhenItIsOlderThanRetentionWindow() {
        List<ProductSearchIndexDescriptor> indices = List.of(
                index("product_20260720140000", 4),
                index("product_20260720130000", 3),
                index("product_20260720120000", 2),
                index("product_20260720110000", 1)
        );

        assertThat(policy.indicesToDelete(indices, Set.of("product_20260720110000")))
                .containsExactly("product_20260720120000");
    }

    @Test
    void ignoresIndicesOutsideStrictManagedNamePattern() {
        List<ProductSearchIndexDescriptor> indices = List.of(
                index("product_v1", 1),
                index("product_2026072012000", 2),
                index("product_20260720120000_backup", 3),
                index("other_20260720120000", 4)
        );

        assertThat(policy.indicesToDelete(indices, Set.of())).isEmpty();
    }

    private ProductSearchIndexDescriptor index(String name, long order) {
        return new ProductSearchIndexDescriptor(name, Instant.ofEpochSecond(order));
    }
}
