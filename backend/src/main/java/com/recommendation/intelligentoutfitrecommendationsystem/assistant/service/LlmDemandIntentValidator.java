package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SlotEvidence;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ValidatedDemandParseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Final Java safety boundary for every untrusted LLM-proposed demand change. */
public class LlmDemandIntentValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmDemandIntentValidator.class);
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

    /** Validate one parser response without trusting Python schema validation. */
    public ValidatedDemandParseResult validate(
            LlmDemandParseResponse response,
            String currentMessage,
            Set<String> lockedSlots,
            PendingClarification currentPending
    ) {
        if (response == null || !"1.0".equals(response.schemaVersion()) || response.slots() == null
                || response.slotConfidence() == null || response.evidence() == null) {
            return rejected("missing_schema_fields");
        }
        Set<String> locks = lockedSlots == null ? Set.of() : lockedSlots;
        return switch (response.action()) {
            case "MERGE" -> validateMerge(response, currentMessage, locks, currentPending);
            case "CLARIFY" -> validateClarify(response, currentMessage, locks, currentPending);
            default -> rejected("unsupported_action");
        };
    }

    private ValidatedDemandParseResult validateMerge(
            LlmDemandParseResponse response,
            String currentMessage,
            Set<String> lockedSlots,
            PendingClarification currentPending
    ) {
        if (response.needsClarification() || response.clarificationSlot() != null
                || response.clarificationCandidateValue() != null || response.clarificationQuestion() != null) {
            return rejected("merge_contains_clarification");
        }
        Map<String, Object> rawSlots = response.slots().presentSlots();
        if (rawSlots.isEmpty() || !response.slotConfidence().keySet().equals(rawSlots.keySet())
                || !response.evidence().keySet().equals(rawSlots.keySet())) {
            return rejected("incomplete_slot_metadata");
        }

        Map<String, Object> accepted = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawSlots.entrySet()) {
            String slot = entry.getKey();
            Object rawValue = entry.getValue();
            Object canonicalValue = normalizer.canonicalValue(slot, rawValue);
            BigDecimal confidence = response.slotConfidence().get(slot);
            List<SlotEvidence> evidence = response.evidence().get(slot);
            if (canonicalValue == null || containsEmptyList(rawValue) || confidence == null
                    || confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0
                    || !evidenceValid(slot, canonicalValue, evidence, currentMessage, currentPending)) {
                return rejected("invalid_" + slot);
            }
            if (lockedSlots.contains(slot)) {
                continue;
            }
            if (confidence.compareTo(THRESHOLDS.get(slot)) < 0) {
                if (HARD_SLOTS.contains(slot)) {
                    return new ValidatedDemandParseResult(null, new PendingClarification(
                            slot, rawValue, confidence, defaultQuestion(slot, rawValue), currentMessage));
                }
                continue;
            }
            if ("budgetMax".equals(slot) && !budgetValid((Integer) canonicalValue)) {
                return rejected("invalid_budget");
            }
            accepted.put(slot, canonicalValue);
        }
        if (accepted.isEmpty()) {
            return rejected("no_accepted_slots");
        }
        return new ValidatedDemandParseResult(toPatch(currentMessage, accepted), null);
    }

    private ValidatedDemandParseResult validateClarify(
            LlmDemandParseResponse response,
            String currentMessage,
            Set<String> lockedSlots,
            PendingClarification currentPending
    ) {
        String slot = response.clarificationSlot();
        String question = response.clarificationQuestion();
        Object candidate = response.clarificationCandidateValue();
        if (!response.needsClarification() || !response.slots().presentSlots().isEmpty()
                || !response.slotConfidence().isEmpty() || !response.evidence().isEmpty()
                || !HARD_SLOTS.contains(slot) || lockedSlots.contains(slot)
                || question == null || question.isBlank() || question.length() > 200) {
            return rejected("invalid_clarify_shape");
        }
        if (candidate != null && normalizer.canonicalValue(slot, candidate) == null) {
            return rejected("invalid_clarify_candidate");
        }
        if (candidate != null && currentPending != null && slot.equals(currentPending.slot())
                && !sameCandidate(slot, candidate, currentPending.candidateValue())) {
            return rejected("pending_candidate_conflict");
        }
        return new ValidatedDemandParseResult(null,
                new PendingClarification(slot, candidate, null, question, currentMessage));
    }

    private boolean evidenceValid(
            String slot,
            Object canonicalValue,
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
                return currentMessage != null && currentMessage.contains(item.text())
                        && normalizer.evidenceCompatible(slot, canonicalValue, item.text());
            }
            return "PENDING_CLARIFICATION".equals(item.source()) && pending != null
                    && slot.equals(pending.slot()) && pending.rawText() != null
                    && pending.rawText().contains(item.text())
                    && sameCandidate(slot, canonicalValue, pending.candidateValue());
        });
    }

    private boolean sameCandidate(String slot, Object first, Object second) {
        Object firstCanonical = normalizer.canonicalValue(slot, first);
        Object secondCanonical = normalizer.canonicalValue(slot, second);
        if (firstCanonical == null && isAlreadyCanonical(slot, first)) {
            firstCanonical = first;
        }
        if (secondCanonical == null && isAlreadyCanonical(slot, second)) {
            secondCanonical = second;
        }
        return firstCanonical != null && firstCanonical.equals(secondCanonical);
    }

    private boolean isAlreadyCanonical(String slot, Object value) {
        if (value == null) {
            return false;
        }
        return switch (slot) {
            case "targetGender" -> Set.of("male", "female", "unisex").contains(value);
            case "category" -> Set.of("外套", "半身裙", "短裤", "衬衫", "上衣", "裤子").contains(value);
            case "budgetMax" -> value instanceof Integer;
            default -> value instanceof List<?>;
        };
    }

    @SuppressWarnings("unchecked")
    private DemandIntentPatch toPatch(String rawQuery, Map<String, Object> values) {
        return new DemandIntentPatch(
                "merge", rawQuery, (String) values.get("targetGender"), false,
                (String) values.get("category"),
                (List<String>) values.getOrDefault("scene", List.of()),
                (List<String>) values.getOrDefault("style", List.of()),
                (Integer) values.get("budgetMax"),
                (List<String>) values.getOrDefault("attributes", List.of())
        );
    }

    private boolean containsEmptyList(Object value) {
        return value instanceof List<?> list && list.isEmpty();
    }

    private boolean budgetValid(Integer budget) {
        return budget != null && budget >= 0 && budget <= 1_000_000;
    }

    private String defaultQuestion(String slot, Object value) {
        return "请确认你的" + slot + "需求是否为“" + value + "”？";
    }

    private ValidatedDemandParseResult rejected(String reason) {
        LOGGER.debug("Rejected demand parse candidate: {}", reason);
        return ValidatedDemandParseResult.rejected();
    }
}
