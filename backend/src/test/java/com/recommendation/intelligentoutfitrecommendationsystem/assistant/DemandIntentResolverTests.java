package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
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

    private AssistantChatRequest request(String message) {
        return new AssistantChatRequest(null, message, null, null, null, null, null, null, null);
    }
}
