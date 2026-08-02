package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.TurnIntent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts the existing parser's current-message patch into v3 turn operations without copying prior state.
 * The v2 patch contract has no provenance or list-removal fields, so populated parser values are treated as
 * current user evidence and removals are limited to its two explicit clear flags plus safe season invalidation.
 */
public class TurnIntentAdapter {

    /**
     * Adapts only values present in this patch; inheritance remains the merger's responsibility.
     *
     * @param turnId stable identity for the current persisted transition
     * @param patch validated output from the current-message parser
     * @return v3 operations containing no copied historical constraints
     */
    public TurnIntent adapt(String turnId, DemandIntentPatch patch) {
        if (patch == null) {
            throw new IllegalArgumentException("demand patch is required");
        }
        Map<String, IntentConstraint> scalars = new LinkedHashMap<>();
        addScalar(scalars, turnId, "targetGender", canonical("targetGender", patch.targetGender()));
        addScalar(scalars, turnId, "category", canonical("category", patch.category()));
        addScalar(scalars, turnId, "season", canonical("season", patch.season()));
        addScalar(scalars, turnId, "budgetMax",
                patch.budgetMax() == null ? null : String.valueOf(patch.budgetMax()));

        List<IntentConstraint> additions = new ArrayList<>();
        addList(additions, turnId, "scene", patch.scene());
        addList(additions, turnId, "style", patch.style());
        addList(additions, turnId, "fitPreferences", patch.fitPreferences());
        addAttributes(additions, turnId, patch.attributes());

        List<IntentConstraint> removals = List.of();
        Set<String> clearFields = new LinkedHashSet<>();
        if (patch.clearTargetGender()) {
            clearFields.add("targetGender");
        }
        if (patch.clearSubjectMeasurements()) {
            clearFields.add("subjectMeasurements");
        }
        if ("reset".equalsIgnoreCase(patch.action())) {
            clearFields.addAll(List.of("targetGender", "category", "season", "budgetMax",
                    "scene", "style", "fitPreferences", "attributes", "thermal", "subjectMeasurements"));
        }
        return new TurnIntent(turnId, patch.rawQuery(), scalars, additions, removals, clearFields,
                patch.requestType(), patch.requestedCapabilities(), patch.subjectMeasurements());
    }

    private void addScalar(Map<String, IntentConstraint> target, String turnId, String field, String value) {
        if (value != null) {
            target.put(field, constraint(turnId, field, List.of(value), ConstraintStrength.HARD));
        }
    }

    private void addList(List<IntentConstraint> target, String turnId, String field, List<String> values) {
        List<String> canonical = values.stream()
                .map(value -> canonical(field, value))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (!canonical.isEmpty()) {
            target.add(constraint(turnId, field, canonical, ConstraintStrength.SOFT));
        }
    }

    private void addAttributes(List<IntentConstraint> target, String turnId, List<String> attributes) {
        Map<String, List<String>> grouped = new LinkedHashMap<>();
        for (String attribute : attributes) {
            CanonicalAttribute canonical = canonicalAttribute(attribute);
            grouped.computeIfAbsent(canonical.field(), ignored -> new ArrayList<>()).add(canonical.value());
        }
        grouped.forEach((field, values) ->
                target.add(constraint(turnId, field, values.stream().distinct().toList(), ConstraintStrength.SOFT)));
    }

    private IntentConstraint constraint(
            String turnId,
            String field,
            List<String> values,
            ConstraintStrength strength
    ) {
        String semantic = turnId + "|" + field + "|" + String.join(",", values) + "|" + strength;
        String id = "turn-" + UUID.nameUUIDFromBytes(semantic.getBytes(StandardCharsets.UTF_8));
        ConstraintOperator operator = strength == ConstraintStrength.HARD
                ? ("budgetMax".equals(field) ? ConstraintOperator.MAX : ConstraintOperator.EQUALS)
                : ConstraintOperator.CONTAINS;
        return new IntentConstraint(id, field, operator,
                values, strength, ConstraintOrigin.USER_EXPLICIT, turnId, null, "ACTIVE_DEMAND", null);
    }

    private String canonical(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (field) {
            case "targetGender" -> switch (normalized) {
                case "male", "\u7537", "\u7537\u6027", "\u7537\u751f" -> "MALE";
                case "female", "\u5973", "\u5973\u6027", "\u5973\u751f" -> "FEMALE";
                case "unisex" -> "UNISEX";
                default -> value.trim().toUpperCase(Locale.ROOT);
            };
            case "season" -> switch (normalized) {
                case "spring", "\u6625", "\u6625\u5929", "\u6625\u5b63" -> "SPRING";
                case "summer", "\u590f", "\u590f\u5929", "\u590f\u5b63" -> "SUMMER";
                case "autumn", "fall", "\u79cb", "\u79cb\u5929", "\u79cb\u5b63" -> "AUTUMN";
                case "winter", "\u51ac", "\u51ac\u5929", "\u51ac\u5b63" -> "WINTER";
                default -> value.trim().toUpperCase(Locale.ROOT);
            };
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private CanonicalAttribute canonicalAttribute(String value) {
        String normalized = value == null ? "" : value.trim();
        if (Set.of("\u4fdd\u6696", "warm").contains(normalized.toLowerCase(Locale.ROOT))) {
            return new CanonicalAttribute("thermal", "WARM");
        }
        if (Set.of("\u539a\u6b3e", "thick").contains(normalized.toLowerCase(Locale.ROOT))) {
            return new CanonicalAttribute("thickness", "THICK");
        }
        return new CanonicalAttribute("attributes", normalized.toUpperCase(Locale.ROOT));
    }

    private record CanonicalAttribute(String field, String value) {
    }
}
