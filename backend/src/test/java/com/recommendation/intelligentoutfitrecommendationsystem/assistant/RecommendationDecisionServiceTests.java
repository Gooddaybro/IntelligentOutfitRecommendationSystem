package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.MatchedDimension;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.RecommendationDecisionService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationDecisionServiceTests {

    private final RecommendationDecisionService service = new RecommendationDecisionService();

    @Test
    void acceptsOnlyCandidateBackedExplicitEvidenceAsStrongMatch() {
        var ref = new PythonProductRef(
                1001L,
                2001L,
                "适合夏季休闲穿搭",
                new BigDecimal("1.35"),
                List.of(
                        new MatchedDimension("season", "summer", "summer", "PRODUCT_SEASON"),
                        new MatchedDimension("style", "casual", "casual", "PRODUCT_STYLE_TAG")
                )
        );

        var decision = service.decide(intent(), List.of(candidate()), List.of(ref));

        assertThat(decision.recommendationStatus()).isEqualTo("STRONG_MATCH");
        assertThat(decision.recommendedItems()).singleElement().satisfies(item -> {
            assertThat(item.spuId()).isEqualTo(1001L);
            assertThat(item.rankScore()).isEqualByComparingTo("1.35");
            assertThat(item.matchedDimensions()).hasSize(2);
            assertThat(item.outfitRole()).isEqualTo("TOP");
        });
    }

    @Test
    void stockOrBaseScoreWithoutExplicitEvidenceStaysWeakFallback() {
        var ref = new PythonProductRef(1001L, 2001L, "库存充足", new BigDecimal("0.35"), List.of());

        var decision = service.decide(intent(), List.of(candidate()), List.of(ref));

        assertThat(decision.recommendationStatus()).isEqualTo("WEAK_FALLBACK");
        assertThat(decision.recommendedItems()).isEmpty();
    }

    @Test
    void rejectsUnknownCandidateAndEvidenceThatContradictsJavaFacts() {
        var unknown = new PythonProductRef(
                9999L, 9999L, "未知商品", BigDecimal.ONE,
                List.of(new MatchedDimension("season", "summer", "summer", "PRODUCT_SEASON"))
        );
        var contradicted = new PythonProductRef(
                1001L, 2001L, "季节不一致", BigDecimal.ONE,
                List.of(new MatchedDimension("season", "summer", "winter", "PRODUCT_SEASON"))
        );

        var decision = service.decide(intent(), List.of(candidate()), List.of(unknown, contradicted));

        assertThat(decision.recommendationStatus()).isEqualTo("WEAK_FALLBACK");
        assertThat(decision.recommendedItems()).isEmpty();
        assertThat(decision.discardedReferences()).isEqualTo(2);
    }

    @Test
    void noHardFilteredCandidatesIsEmptyEvenWhenPythonReturnsReferences() {
        var ref = new PythonProductRef(
                1001L, 2001L, "不应被采信", BigDecimal.ONE,
                List.of(new MatchedDimension("season", "summer", "summer", "PRODUCT_SEASON"))
        );

        var decision = service.decide(intent(), List.of(), List.of(ref));

        assertThat(decision.recommendationStatus()).isEqualTo("EMPTY");
        assertThat(decision.recommendedItems()).isEmpty();
    }

    private DemandIntent intent() {
        return new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "夏天休闲穿搭",
                "OUTFIT_ADVICE", List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
                "male", null, "summer", List.of(), List.of("casual"), List.of("relaxed"),
                null, List.of(), null, List.of("targetGender", "season"),
                List.of("style", "fitPreferences"), new BigDecimal("0.80"), List.of()
        );
    }

    private RecommendationCandidate candidate() {
        return new RecommendationCandidate(
                1001L, 2001L, "SPU-1001", "夏季休闲T恤", "T恤", null,
                "relaxed", "黑色", "L", "棉", "summer", "casual",
                new BigDecimal("139"), "in_stock", new BigDecimal("139"), new BigDecimal("139"),
                8, "SKU-2001", 8, "风格:休闲,版型:宽松"
        );
    }
}
