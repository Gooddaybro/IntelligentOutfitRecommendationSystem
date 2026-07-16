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

    @Test
    void rejectsUnknownActionAndIllegalSoftEnum() {
        var unknownAction = new LlmDemandParseResponse(
                "1.0", "GUESS", new LlmDemandSlots("FEMALE", null, null, null, null, null),
                Map.of("targetGender", new BigDecimal("0.93")),
                Map.of("targetGender", List.of(new SlotEvidence("女性", "CURRENT_MESSAGE"))),
                false, null, null, null);
        var illegalSoft = new LlmDemandParseResponse(
                "1.0", "MERGE", new LlmDemandSlots(null, null, null,
                List.of("INVENTED"), null, null),
                Map.of("style", new BigDecimal("0.90")),
                Map.of("style", List.of(new SlotEvidence("随便", "CURRENT_MESSAGE"))),
                false, null, null, null);

        assertThat(validator.validate(unknownAction, "女性", Set.of(), null).patch()).isNull();
        assertThat(validator.validate(illegalSoft, "随便", Set.of(), null).patch()).isNull();
    }

    @Test
    void acceptsDocumentedClarifyShapeButRejectsLockedClarification() {
        var clarify = new LlmDemandParseResponse(
                "1.0", "CLARIFY", new LlmDemandSlots(null, null, null, null, null, null),
                Map.of(), Map.of(), true, "targetGender", "FEMALE", "确认要筛选女士商品吗？");

        var accepted = validator.validate(clarify, "给对象买衣服", Set.of(), null);
        var locked = validator.validate(clarify, "女性穿搭", Set.of("targetGender"), null);

        assertThat(accepted.pendingClarification().candidateValue()).isEqualTo("FEMALE");
        assertThat(locked.pendingClarification()).isNull();
    }

    @Test
    void pendingEvidenceCannotChangeCandidateValue() {
        var pending = new com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification(
                "targetGender", "MALE", new BigDecimal("0.70"), "确认筛选男士商品吗？", "给男朋友买");
        var response = response("FEMALE", new BigDecimal("0.93"),
                new SlotEvidence("男朋友", "PENDING_CLARIFICATION"));

        var result = validator.validate(response, "是", Set.of(), pending);

        assertThat(result.patch()).isNull();
    }

    private LlmDemandParseResponse response(String gender, BigDecimal confidence, SlotEvidence evidence) {
        return new LlmDemandParseResponse(
                "1.0", "MERGE", new LlmDemandSlots(gender, null, null, null, null, null),
                Map.of("targetGender", confidence), Map.of("targetGender", List.of(evidence)),
                false, null, null
        );
    }
}
