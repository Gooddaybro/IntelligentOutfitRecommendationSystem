package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandSlots;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SlotEvidence;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.LlmDemandIntentValidator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmDemandIntentValidatorTests {

    private final LlmDemandIntentValidator validator = new LlmDemandIntentValidator();

    @Test
    void acceptsSupportedEnumWithExactCurrentMessageEvidence() {
        var result = validator.validate(response("FEMALE", new BigDecimal("0.93"),
                new SlotEvidence("女朋友", "CURRENT_MESSAGE")), "给女朋友找衣服", Set.of(), null);

        assertThat(result.patch()).isNotNull();
        assertThat(result.patch().targetGender()).isEqualTo("female");
        assertThat(result.pendingClarification()).isNull();
    }

    @Test
    void rejectsLockedSlotAndInventedEvidence() {
        var locked = validator.validate(response("FEMALE", new BigDecimal("0.93"),
                new SlotEvidence("女朋友", "CURRENT_MESSAGE")), "给女朋友找衣服",
                Set.of("targetGender"), null);
        var invented = validator.validate(response("FEMALE", new BigDecimal("0.93"),
                new SlotEvidence("女性", "CURRENT_MESSAGE")), "给女朋友找衣服", Set.of(), null);

        assertThat(locked.patch()).isNull();
        assertThat(invented.patch()).isNull();
    }

    @Test
    void lowConfidenceHardSlotBecomesPendingInsteadOfSqlPatch() {
        var result = validator.validate(response("FEMALE", new BigDecimal("0.70"),
                new SlotEvidence("她", "CURRENT_MESSAGE")), "她适合什么衣服", Set.of(), null);

        assertThat(result.patch()).isNull();
        assertThat(result.pendingClarification()).isNotNull();
        assertThat(result.pendingClarification().slot()).isEqualTo("targetGender");
    }

    private LlmDemandParseResponse response(String gender, BigDecimal confidence, SlotEvidence evidence) {
        return new LlmDemandParseResponse(
                "1.0", "MERGE", new LlmDemandSlots(gender, null, null, null, null, null),
                Map.of("targetGender", confidence), Map.of("targetGender", List.of(evidence)),
                false, null, null
        );
    }
}
