package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DeterministicDemandParseResult;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentParseTrigger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemandIntentParseTriggerTests {

    private final DemandIntentParseTrigger trigger = new DemandIntentParseTrigger();

    @Test
    void triggersForPendingOrMeaningfulUnresolvedShoppingText() {
        assertThat(trigger.shouldParse(result("成熟硬朗", true), true)).isTrue();
        assertThat(trigger.shouldParse(result("成熟硬朗", true), false)).isTrue();
    }

    @Test
    void doesNotTriggerForConnectorsOrNonShoppingQuestions() {
        assertThat(trigger.shouldParse(result("然后呢", true), false)).isFalse();
        assertThat(trigger.shouldParse(result("订单什么时候发货", false), false)).isFalse();
        assertThat(trigger.shouldParse(result("订单什么时候发货", false), true)).isFalse();
    }

    private DeterministicDemandParseResult result(String unresolved, boolean shoppingSignal) {
        return new DeterministicDemandParseResult(
                new DemandIntentPatch("merge", unresolved, null, false, null,
                        List.of(), List.of(), null, List.of()),
                List.of(), List.of(), unresolved, shoppingSignal
        );
    }
}
