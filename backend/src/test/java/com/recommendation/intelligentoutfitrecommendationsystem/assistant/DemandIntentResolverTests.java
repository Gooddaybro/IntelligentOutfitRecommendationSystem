package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DeterministicDemandParseResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DemandIntentResolverTests {

    private final DemandIntentResolver resolver = new DemandIntentResolver();

    @Test
    void exposesLockedGenderAndShoppingSignal() {
        DeterministicDemandParseResult result = resolver.resolveDetailed(request("男性穿搭"));

        assertThat(result.deterministicPatch().targetGender()).isEqualTo("male");
        assertThat(result.lockedSlots()).contains("targetGender");
        assertThat(result.matchedFragments()).contains("男性");
        assertThat(result.hasShoppingSignal()).isTrue();
    }

    @Test
    void leavesUnknownStyleLanguageForSemanticParsing() {
        DeterministicDemandParseResult result = resolver.resolveDetailed(request("给女朋友找成熟硬朗外套"));

        assertThat(result.deterministicPatch().targetGender()).isEqualTo("female");
        assertThat(result.deterministicPatch().category()).isEqualTo("外套");
        assertThat(result.unresolvedText()).contains("成熟").contains("硬朗");
    }

    @Test
    void resolvesGreetingPlusOutfitQuestionAsOutfitAdvice() {
        DemandIntentPatch patch = resolver.resolvePatch(request("你好，我想要夏天休闲一点的穿搭，应该怎么穿？"));

        assertThat(patch.requestType()).isEqualTo("OUTFIT_ADVICE");
        assertThat(patch.requestedCapabilities()).containsExactly("OUTFIT_PLAN", "PRODUCT_SELECTION");
        assertThat(patch.season()).isEqualTo("summer");
        assertThat(patch.style()).contains("casual");
    }

    @Test
    void resolvesTheApprovedNaturalLanguageAcceptanceCase() {
        DemandIntentPatch patch = resolver.resolvePatch(request(
                "你好，我想要轻松一点的，我177 130 夏天的衣服该怎么穿呢？男性"
        ));

        assertThat(patch.requestType()).isEqualTo("OUTFIT_ADVICE");
        assertThat(patch.requestedCapabilities()).containsExactly("OUTFIT_PLAN", "PRODUCT_SELECTION");
        assertThat(patch.targetGender()).isEqualTo("male");
        assertThat(patch.season()).isEqualTo("summer");
        assertThat(patch.style()).contains("casual");
        assertThat(patch.fitPreferences()).contains("relaxed");
        assertThat(patch.subjectMeasurements().heightCm()).isEqualByComparingTo("177");
        assertThat(patch.subjectMeasurements().weightKg()).isEqualByComparingTo("65");
        assertThat(patch.subjectMeasurements().normalizedFrom()).isEqualTo("ASSUMED_JIN");
    }

    @Test
    void normalizesBareChineseMeasurementPairAndRetainsTheAssumption() {
        DemandIntentPatch patch = resolver.resolvePatch(request("我177 130，夏天怎么穿？"));

        assertThat(patch.subjectMeasurements().heightCm()).isEqualByComparingTo("177");
        assertThat(patch.subjectMeasurements().weightKg()).isEqualByComparingTo("65");
        assertThat(patch.subjectMeasurements().originalText()).isEqualTo("我177 130");
        assertThat(patch.subjectMeasurements().normalizedFrom()).isEqualTo("ASSUMED_JIN");
        assertThat(patch.subjectMeasurements().subject()).isEqualTo("SELF");
        assertThat(patch.subjectMeasurements().scope()).isEqualTo("ACTIVE_DEMAND");
        assertThat(patch.subjectMeasurements().source()).isEqualTo("CURRENT_MESSAGE");
    }

    @Test
    void distinguishesOtherAndUnknownMeasurementSubjects() {
        DemandIntentPatch other = resolver.resolvePatch(request("我朋友178 140怎么穿"));
        DemandIntentPatch unknown = resolver.resolvePatch(request("177 130 夏天怎么穿"));

        assertThat(other.subjectMeasurements().subject()).isEqualTo("OTHER");
        assertThat(unknown.subjectMeasurements().subject()).isEqualTo("UNKNOWN");
    }

    @Test
    void onlyExplicitSizeQuestionBecomesSizeMainTask() {
        DemandIntentPatch outfitWithSize = resolver.resolvePatch(request("177 130 夏天怎么穿，顺便看看尺码"));
        DemandIntentPatch sizeOnly = resolver.resolvePatch(request("177 130 穿什么码"));

        assertThat(outfitWithSize.requestType()).isEqualTo("OUTFIT_ADVICE");
        assertThat(outfitWithSize.requestedCapabilities())
                .containsExactly("OUTFIT_PLAN", "PRODUCT_SELECTION", "SIZE_GUIDANCE");
        assertThat(sizeOnly.requestType()).isEqualTo("SIZE_RECOMMENDATION");
        assertThat(sizeOnly.requestedCapabilities()).containsExactly("SIZE_GUIDANCE");
    }

    @Test
    void keepsAnExplicitFilterSeasonInTheUnifiedDemand() {
        AssistantChatRequest request = new AssistantChatRequest(
                null, "recommend a jacket", null, null, "autumn", null, null, null, null
        );

        DemandIntentPatch patch = resolver.resolvePatch(request);

        assertThat(patch.season()).isEqualTo("autumn");
    }

    private AssistantChatRequest request(String message) {
        return new AssistantChatRequest(null, message, null, null, null, null, null, null, null);
    }
}
