package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchDocument;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchIndexRow;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchIndexRowTests {

    @Test
    void convertsDatabaseAggregatesIntoStableSearchArrays() {
        ProductSearchIndexRow row = new ProductSearchIndexRow(
                1106L, "JACKET_001", "黑色硬朗风皮夹克", "机车风外套", "外套", "合身",
                "皮革,聚酯纤维,皮革", "hard,通勤", "通勤,户外", "autumn,winter", "on_sale");
        Instant rebuiltAt = Instant.parse("2026-07-20T08:00:00Z");

        ProductSearchDocument document = row.toDocument(rebuiltAt);

        assertThat(document.materials()).containsExactly("皮革", "聚酯纤维");
        assertThat(document.styles()).containsExactly("hard", "通勤");
        assertThat(document.scenes()).containsExactly("通勤", "户外");
        assertThat(document.seasons()).containsExactly("autumn", "winter");
        assertThat(document.updatedAt()).isEqualTo(rebuiltAt);
    }
}
