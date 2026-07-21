package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.TurnIntent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges current-turn explicit operations into the effective demand using field-specific policies.
 * This boundary never infers derived preferences; derivation belongs exclusively to
 * {@link DerivedConstraintResolver}.
 */
public class IntentConstraintMerger {

    private static final Set<String> SCALAR_FIELDS =
            Set.of("targetGender", "category", "season", "budgetMax");
    private static final Set<String> REPLACE_LIST_FIELDS = Set.of("style", "fitPreferences");
    private static final Set<String> APPEND_LIST_FIELDS = Set.of("scene", "attributes", "thermal");

    /**
     * Applies clear and remove operations before current additions, preserving fields omitted by the current turn.
     * Request metadata and measurements inherit from the previous snapshot unless the turn explicitly supplies them.
     *
     * @param previous prior effective state, or {@code null} for the first turn
     * @param turn validated current-turn operations
     * @return a merged snapshot containing explicit constraints only as supplied, without new derived values
     */
    public EffectiveDemand merge(EffectiveDemand previous, TurnIntent turn) {
        if (turn == null) {
            throw new IllegalArgumentException("turn intent is required");
        }
        EffectiveDemand base = previous == null
                ? EffectiveDemand.v3("", null, List.of(), List.of(), List.of(), null)
                : previous;
        List<IntentConstraint> hard = new ArrayList<>(base.hardFilters());
        List<IntentConstraint> soft = new ArrayList<>(base.softPreferences());

        turn.clearFields().forEach(field -> removeField(hard, soft, field));
        turn.explicitRemovals().forEach(removal -> removeSemanticMatch(hard, soft, removal));

        for (Map.Entry<String, IntentConstraint> replacement : turn.scalarReplacements().entrySet()) {
            if (!SCALAR_FIELDS.contains(replacement.getKey())) {
                throw new IllegalArgumentException("unsupported scalar field: " + replacement.getKey());
            }
            removeField(hard, soft, replacement.getKey());
            addByStrength(hard, soft, replacement.getValue());
        }

        Set<String> replacedFields = turn.explicitAdditions().stream()
                .map(IntentConstraint::field)
                .filter(REPLACE_LIST_FIELDS::contains)
                .collect(java.util.stream.Collectors.toSet());
        replacedFields.forEach(field -> removeField(hard, soft, field));
        for (IntentConstraint addition : turn.explicitAdditions()) {
            if (!REPLACE_LIST_FIELDS.contains(addition.field())
                    && !APPEND_LIST_FIELDS.contains(addition.field())) {
                throw new IllegalArgumentException("unsupported list field: " + addition.field());
            }
            addByStrength(hard, soft, addition);
        }

        String requestType = turn.requestType() == null ? base.requestType() : turn.requestType();
        List<String> capabilities = turn.requestType() == null && turn.requestedCapabilities().isEmpty()
                ? base.requestedCapabilities() : turn.requestedCapabilities();
        var measurements = turn.clearFields().contains("subjectMeasurements")
                ? null
                : turn.subjectMeasurements() == null ? base.subjectMeasurements() : turn.subjectMeasurements();
        return EffectiveDemand.v3(turn.rawQuery(), requestType, capabilities,
                deduplicate(hard), deduplicate(soft), measurements);
    }

    private void removeField(List<IntentConstraint> hard, List<IntentConstraint> soft, String field) {
        hard.removeIf(item -> item.field().equals(field));
        soft.removeIf(item -> item.field().equals(field));
    }

    private void removeSemanticMatch(
            List<IntentConstraint> hard,
            List<IntentConstraint> soft,
            IntentConstraint removal
    ) {
        hard.removeIf(item -> sameSemanticValue(item, removal));
        soft.removeIf(item -> sameSemanticValue(item, removal));
    }

    private boolean sameSemanticValue(IntentConstraint left, IntentConstraint right) {
        return left.field().equals(right.field())
                && left.operator() == right.operator()
                && left.values().equals(right.values());
    }

    private void addByStrength(
            List<IntentConstraint> hard,
            List<IntentConstraint> soft,
            IntentConstraint constraint
    ) {
        switch (constraint.strength()) {
            case HARD -> hard.add(constraint);
            case SOFT -> soft.add(constraint);
        }
    }

    private List<IntentConstraint> deduplicate(List<IntentConstraint> constraints) {
        Map<String, IntentConstraint> unique = new LinkedHashMap<>();
        constraints.forEach(item -> unique.merge(semanticKey(item), item, this::preferExplicit));
        return List.copyOf(unique.values());
    }

    private IntentConstraint preferExplicit(IntentConstraint existing, IntentConstraint candidate) {
        if (candidate.origin() == ConstraintOrigin.USER_EXPLICIT
                && existing.origin() != ConstraintOrigin.USER_EXPLICIT) {
            return candidate;
        }
        return existing;
    }

    private String semanticKey(IntentConstraint constraint) {
        return constraint.field() + "\u0000" + constraint.operator() + "\u0000" + constraint.values();
    }
}
