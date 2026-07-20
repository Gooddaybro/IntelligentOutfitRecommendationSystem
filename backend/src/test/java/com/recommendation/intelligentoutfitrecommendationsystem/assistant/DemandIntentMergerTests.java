package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SubjectMeasurements;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentMerger;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentResolver;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemandIntentMergerTests {

    private final DemandIntentResolver resolver = new DemandIntentResolver();
    private final DemandIntentMerger merger = new DemandIntentMerger();

    @Test
    void switchesGenderWithoutDroppingExistingDemand() {
        DemandIntent previous = intent("male", "外套", List.of("commute"), 500);

        DemandIntent result = merger.merge(previous, resolver.resolvePatch(request("那女性呢？")));

        assertThat(result.targetGender()).isEqualTo("female");
        assertThat(result.category()).isEqualTo("外套");
        assertThat(result.scene()).containsExactly("commute");
        assertThat(result.budgetMax()).isEqualTo(500);
    }

    @Test
    void latestExplicitGenderWinsAcrossRepeatedSwitches() {
        DemandIntent female = merger.merge(intent("male", "外套", List.of("commute"), null),
                resolver.resolvePatch(request("那女性呢？")));

        DemandIntent male = merger.merge(female, resolver.resolvePatch(request("还是看看男性")));

        assertThat(female.targetGender()).isEqualTo("female");
        assertThat(male.targetGender()).isEqualTo("male");
        assertThat(male.category()).isEqualTo("外套");
    }

    @Test
    void clearAndCompareHaveDifferentStateSemantics() {
        DemandIntent previous = intent("male", "外套", List.of("commute"), null);

        DemandIntent compared = merger.merge(previous, resolver.resolvePatch(request("男款和女款有什么区别")));
        DemandIntent cleared = merger.merge(previous, resolver.resolvePatch(request("男女都可以")));

        assertThat(compared).isEqualTo(previous);
        assertThat(cleared.targetGender()).isNull();
        assertThat(cleared.category()).isEqualTo("外套");
    }

    @Test
    void resetClearsPreviousDemand() {
        DemandIntent result = merger.merge(
                intent("male", "外套", List.of("commute"), 500),
                resolver.resolvePatch(request("重新开始"))
        );

        assertThat(result.targetGender()).isNull();
        assertThat(result.category()).isNull();
        assertThat(result.scene()).isEmpty();
        assertThat(result.budgetMax()).isNull();
    }

    @Test
    void shortGenderInputCreatesAUsablePatch() {
        DemandIntentPatch patch = resolver.resolvePatch(request("男性"));

        assertThat(patch.action()).isEqualTo("initialize");
        assertThat(patch.targetGender()).isEqualTo("male");
    }

    @Test
    void keepsTheCurrentTaskWhenTheNextTurnOnlyAddsAFitPreference() {
        DemandIntent previous = outfitIntent(null);

        DemandIntent result = merger.merge(previous, resolver.resolvePatch(request("再宽松一点")));

        assertThat(result.requestType()).isEqualTo("OUTFIT_ADVICE");
        assertThat(result.requestedCapabilities()).containsExactly("OUTFIT_PLAN", "PRODUCT_SELECTION");
        assertThat(result.fitPreferences()).contains("relaxed");
        assertThat(result.softPreferences()).contains("fitPreferences");
    }

    @Test
    void clearsSelfMeasurementsWhenConsultationSwitchesToAnotherPerson() {
        SubjectMeasurements self = new SubjectMeasurements(
                new BigDecimal("177"), new BigDecimal("65"), "我年177 130",
                "ASSUMED_JIN", "SELF", "ACTIVE_DEMAND", "CURRENT_MESSAGE"
        );
        DemandIntent previous = outfitIntent(self);

        DemandIntent result = merger.merge(previous, resolver.resolvePatch(request("改为给我朋友看看男款外套")));

        assertThat(result.subjectMeasurements()).isNull();
    }

    @Test
    void addsSeasonToHardFiltersAndUsesVersionTwo() {
        DemandIntent result = merger.merge(null, resolver.resolvePatch(request("夏天怎么穿")));

        assertThat(result.version()).isEqualTo("demand-intent-v2");
        assertThat(result.season()).isEqualTo("summer");
        assertThat(result.hardFilters()).contains("season");
    }

    private AssistantChatRequest request(String message) {
        return new AssistantChatRequest(null, message, null, null, null, null, null, null, null);
    }

    private DemandIntent intent(String gender, String category, List<String> scene, Integer budget) {
        return new DemandIntent(
                DemandIntent.VERSION,
                DemandIntent.SOURCE_JAVA_RULE,
                "previous",
                null,
                List.of(),
                gender,
                category,
                null,
                scene,
                List.of("minimal"),
                List.of(),
                budget,
                List.of(),
                null,
                List.of("targetGender", "category"),
                List.of("scene", "style"),
                new BigDecimal("0.80"),
                List.of()
        );
    }

    private DemandIntent outfitIntent(SubjectMeasurements measurements) {
        return new DemandIntent(
                DemandIntent.VERSION,
                DemandIntent.SOURCE_JAVA_RULE,
                "previous",
                "OUTFIT_ADVICE",
                List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
                "male",
                null,
                "summer",
                List.of(),
                List.of("casual"),
                List.of(),
                null,
                List.of(),
                measurements,
                List.of("targetGender", "season"),
                List.of("style"),
                new BigDecimal("0.80"),
                List.of()
        );
    }
}
