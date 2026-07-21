package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Typed assistant view of independently stored effective demand, clarification workflow and conflict diagnosis.
 * The v2 projection is retained only at existing controller and Python boundaries during the v3 rollout.
 */
public record DemandIntentStateSnapshot(
        EffectiveDemand effectiveDemand,
        PendingClarification pendingClarification,
        ConstraintConflictResult conflictResult
) {
    public DemandIntentStateSnapshot {
        if (effectiveDemand == null) {
            throw new IllegalArgumentException("effective demand is required");
        }
        conflictResult = conflictResult == null
                ? new ConstraintConflictResult(ConstraintConflictStatus.VALID, List.of(), "") : conflictResult;
    }

    /** Creates a snapshot when no persisted conflict metadata exists yet. */
    public DemandIntentStateSnapshot(EffectiveDemand effectiveDemand, PendingClarification pendingClarification) {
        this(effectiveDemand, pendingClarification,
                new ConstraintConflictResult(ConstraintConflictStatus.VALID, List.of(), ""));
    }

    /**
     * Projects v3 state to the established v2 response boundary without re-merging or changing provenance.
     *
     * @return a compatibility DTO for callers not yet migrated to {@link EffectiveDemand}
     */
    public DemandIntent effectiveIntent() {
        return toLegacyIntent(effectiveDemand);
    }

    /**
     * Converts v3 at an explicit legacy output boundary. This projection is intentionally lossy: provenance and
     * derived fields that v2 cannot represent are omitted rather than mislabeled as Java-rule evidence.
     */
    public static DemandIntent toLegacyIntent(EffectiveDemand effectiveDemand) {
        String gender = firstValue(effectiveDemand, "targetGender");
        String category = firstValue(effectiveDemand, "category");
        String season = firstValue(effectiveDemand, "season");
        Integer budget = integerValue(effectiveDemand, "budgetMax");
        List<String> scene = listValues(effectiveDemand, "scene");
        List<String> style = listValues(effectiveDemand, "style");
        List<String> fit = listValues(effectiveDemand, "fitPreferences");
        LinkedHashSet<String> attributes = new LinkedHashSet<>(listValues(effectiveDemand, "attributes"));
        effectiveDemand.constraints("thermal").stream()
                .flatMap(item -> item.values().stream())
                .filter("WARM"::equals)
                .findFirst().ifPresent(ignored -> attributes.add("\u4fdd\u6696"));
        effectiveDemand.constraints("thickness").stream()
                .flatMap(item -> item.values().stream())
                .filter("THICK"::equals)
                .findFirst().ifPresent(ignored -> attributes.add("\u539a\u6b3e"));
        LinkedHashSet<String> hardFields = new LinkedHashSet<>();
        addFieldWhenPresent(hardFields, "targetGender", gender);
        addFieldWhenPresent(hardFields, "category", category);
        addFieldWhenPresent(hardFields, "season", season);
        addFieldWhenPresent(hardFields, "budgetMax", budget);
        LinkedHashSet<String> softFields = new LinkedHashSet<>();
        addFieldWhenPresent(softFields, "scene", scene);
        addFieldWhenPresent(softFields, "style", style);
        addFieldWhenPresent(softFields, "fitPreferences", fit);
        addFieldWhenPresent(softFields, "attributes", attributes);
        return new DemandIntent(
                DemandIntent.VERSION, "v3-projection", effectiveDemand.rawQuery(),
                effectiveDemand.requestType(), effectiveDemand.requestedCapabilities(),
                lower(gender), category, lower(season), scene, style, fit, budget, List.copyOf(attributes),
                effectiveDemand.subjectMeasurements(), List.copyOf(hardFields), List.copyOf(softFields),
                hardFields.isEmpty() && softFields.isEmpty() ? BigDecimal.ZERO : new BigDecimal("0.80"), List.of());
    }

    private static void addFieldWhenPresent(LinkedHashSet<String> fields, String field, Object value) {
        if (value instanceof java.util.Collection<?> collection ? !collection.isEmpty() : value != null) {
            fields.add(field);
        }
    }

    private static String firstValue(EffectiveDemand effectiveDemand, String field) {
        return effectiveDemand.value(field).orElse(null);
    }

    private static Integer integerValue(EffectiveDemand effectiveDemand, String field) {
        String value = firstValue(effectiveDemand, field);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static List<String> listValues(EffectiveDemand effectiveDemand, String field) {
        return effectiveDemand.constraints(field).stream()
                .flatMap(item -> item.values().stream())
                .map(DemandIntentStateSnapshot::lowerAsciiCanonical)
                .distinct()
                .toList();
    }

    private static String lower(String value) {
        return value == null ? null : lowerAsciiCanonical(value);
    }

    private static String lowerAsciiCanonical(String value) {
        return value.matches("[A-Z_]+") ? value.toLowerCase(Locale.ROOT) : value;
    }
}
