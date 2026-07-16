package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SlotEvidence;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ValidatedDemandParseResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final Java safety boundary for LLM-proposed demand changes. */
public class LlmDemandIntentValidator {

    private static final Map<String, BigDecimal> THRESHOLDS = Map.of(
            "targetGender", new BigDecimal("0.85"),
            "category", new BigDecimal("0.80"),
            "budgetMax", new BigDecimal("0.95"),
            "scene", new BigDecimal("0.65"),
            "style", new BigDecimal("0.65"),
            "attributes", new BigDecimal("0.65")
    );
    private static final Set<String> HARD_SLOTS = Set.of("targetGender", "category", "budgetMax");
    private final DemandIntentNormalizer normalizer = new DemandIntentNormalizer();

    public ValidatedDemandParseResult validate(
            LlmDemandParseResponse response,
            String currentMessage,
            Set<String> lockedSlots,
            PendingClarification currentPending
    ) {
        if (response == null || !"1.0".equals(response.schemaVersion()) || response.slots() == null
                || response.slotConfidence() == null || response.evidence() == null) {
            return ValidatedDemandParseResult.rejected();
        }
        String hardPendingSlot = null;
        Object hardPendingValue = null;
        BigDecimal hardPendingConfidence = null;

        String gender = acceptedValue("targetGender", response.slots().targetGender(), response,
                currentMessage, lockedSlots, currentPending) ? normalizer.gender(response.slots().targetGender()) : null;
        String category = acceptedValue("category", response.slots().category(), response,
                currentMessage, lockedSlots, currentPending) ? normalizer.category(response.slots().category()) : null;
        Integer budget = acceptedValue("budgetMax", response.slots().budgetMax(), response,
                currentMessage, lockedSlots, currentPending) ? response.slots().budgetMax() : null;

        for (String slot : HARD_SLOTS) {
            Object value = slotValue(response, slot);
            BigDecimal confidence = response.slotConfidence().get(slot);
            if (value != null && evidenceValid(slot, response.evidence().get(slot), currentMessage, currentPending)
                    && !lockedSlots.contains(slot) && confidence != null
                    && confidence.compareTo(THRESHOLDS.get(slot)) < 0) {
                hardPendingSlot = slot;
                hardPendingValue = value;
                hardPendingConfidence = confidence;
                break;
            }
        }
        if (hardPendingSlot != null || "CLARIFY".equals(response.action())) {
            String slot = response.clarificationSlot() != null ? response.clarificationSlot() : hardPendingSlot;
            Object value = hardPendingValue != null ? hardPendingValue : slotValue(response, slot);
            BigDecimal confidence = hardPendingConfidence != null
                    ? hardPendingConfidence : response.slotConfidence().get(slot);
            String question = response.clarificationQuestion() != null
                    ? response.clarificationQuestion() : defaultQuestion(slot, value);
            return new ValidatedDemandParseResult(null,
                    new PendingClarification(slot, value, confidence, question, currentMessage));
        }

        List<String> scene = acceptedValue("scene", response.slots().scene(), response,
                currentMessage, lockedSlots, currentPending) ? normalizer.softValues(response.slots().scene()) : List.of();
        List<String> style = acceptedValue("style", response.slots().style(), response,
                currentMessage, lockedSlots, currentPending) ? normalizer.softValues(response.slots().style()) : List.of();
        List<String> attributes = acceptedValue("attributes", response.slots().attributes(), response,
                currentMessage, lockedSlots, currentPending)
                ? normalizer.softValues(response.slots().attributes()) : List.of();
        if (gender == null && category == null && budget == null
                && scene.isEmpty() && style.isEmpty() && attributes.isEmpty()) {
            return ValidatedDemandParseResult.rejected();
        }
        return new ValidatedDemandParseResult(new DemandIntentPatch(
                "merge", currentMessage, gender, false, category, scene, style, budget, attributes), null);
    }

    private boolean acceptedValue(
            String slot,
            Object value,
            LlmDemandParseResponse response,
            String currentMessage,
            Set<String> lockedSlots,
            PendingClarification currentPending
    ) {
        if (value == null || lockedSlots.contains(slot)) {
            return false;
        }
        BigDecimal confidence = response.slotConfidence().get(slot);
        if (confidence == null || confidence.compareTo(THRESHOLDS.get(slot)) < 0) {
            return false;
        }
        if (!evidenceValid(slot, response.evidence().get(slot), currentMessage, currentPending)) {
            return false;
        }
        if ("targetGender".equals(slot) && normalizer.gender((String) value) == null) {
            return false;
        }
        if ("category".equals(slot) && normalizer.category((String) value) == null) {
            return false;
        }
        return !"budgetMax".equals(slot) || budgetEvidenceValid((Integer) value, response.evidence().get(slot));
    }

    private boolean evidenceValid(
            String slot,
            List<SlotEvidence> evidence,
            String currentMessage,
            PendingClarification pending
    ) {
        if (evidence == null || evidence.isEmpty()) {
            return false;
        }
        return evidence.stream().allMatch(item -> {
            if (item == null || item.text() == null || item.text().isBlank()) {
                return false;
            }
            if ("CURRENT_MESSAGE".equals(item.source())) {
                return currentMessage != null && currentMessage.contains(item.text());
            }
            return "PENDING_CLARIFICATION".equals(item.source()) && pending != null
                    && slot.equals(pending.slot()) && pending.rawText() != null
                    && pending.rawText().contains(item.text());
        });
    }

    private boolean budgetEvidenceValid(Integer budget, List<SlotEvidence> evidence) {
        return budget != null && budget >= 0 && budget <= 1_000_000
                && evidence.stream().anyMatch(item -> item.text().matches(".*\\b" + budget + "\\b.*"));
    }

    private Object slotValue(LlmDemandParseResponse response, String slot) {
        return switch (slot) {
            case "targetGender" -> response.slots().targetGender();
            case "category" -> response.slots().category();
            case "budgetMax" -> response.slots().budgetMax();
            case "scene" -> response.slots().scene();
            case "style" -> response.slots().style();
            case "attributes" -> response.slots().attributes();
            default -> null;
        };
    }

    private String defaultQuestion(String slot, Object value) {
        return "请确认你的" + slot + "需求是否为“" + value + "”？";
    }
}
