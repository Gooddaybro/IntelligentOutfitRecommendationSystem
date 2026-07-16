package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DeterministicDemandParseResult;

import java.util.Set;

/** Pure policy deciding whether a turn needs semantic demand parsing. */
public class DemandIntentParseTrigger {

    private static final Set<String> GENERIC_LEFTOVERS = Set.of(
            "", "然后", "然后呢", "呢", "还有呢", "可以", "好的", "是", "确认"
    );

    public boolean shouldParse(DeterministicDemandParseResult result, boolean hasPendingClarification) {
        if (result == null) {
            return false;
        }
        if (isNonShoppingInterrupt(result.deterministicPatch().rawQuery())) {
            return false;
        }
        if (hasPendingClarification) {
            return true;
        }
        if (!result.hasShoppingSignal()) {
            return false;
        }
        String unresolved = result.unresolvedText().replaceAll("[，。！？,.!?\\s]", "");
        if (GENERIC_LEFTOVERS.contains(unresolved)) {
            return false;
        }
        return hasDeterministicSlots(result) || !unresolved.isBlank();
    }

    private boolean isNonShoppingInterrupt(String message) {
        if (message == null) {
            return false;
        }
        return Set.of("订单", "物流", "发货", "退款", "退货", "售后", "库存政策")
                .stream().anyMatch(message::contains);
    }

    private boolean hasDeterministicSlots(DeterministicDemandParseResult result) {
        var patch = result.deterministicPatch();
        return patch != null && (patch.targetGender() != null
                || patch.category() != null
                || patch.budgetMax() != null
                || !patch.scene().isEmpty()
                || !patch.style().isEmpty()
                || !patch.attributes().isEmpty());
    }
}
