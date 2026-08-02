package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Immutable Java-authoritative demand after merging and derivation.
 * Candidate filtering and downstream recommendation contracts consume this snapshot, not turn patches.
 */
public record EffectiveDemand(
        String version,
        String rawQuery,
        String requestType,
        List<String> requestedCapabilities,
        List<IntentConstraint> hardFilters,
        List<IntentConstraint> softPreferences,
        SubjectMeasurements subjectMeasurements
) {
    public static final String VERSION = "demand-intent-v3";

    public EffectiveDemand {
        version = version == null || version.isBlank() ? VERSION : version;
        rawQuery = rawQuery == null ? "" : rawQuery;
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
        hardFilters = hardFilters == null ? List.of() : List.copyOf(hardFilters);
        softPreferences = softPreferences == null ? List.of() : List.copyOf(softPreferences);
        requireStrength(hardFilters, ConstraintStrength.HARD, "hardFilters");
        requireStrength(softPreferences, ConstraintStrength.SOFT, "softPreferences");
    }

    /** Creates the current contract version while preserving the hard/soft filtering boundary. */
    public static EffectiveDemand v3(
            String rawQuery,
            String requestType,
            List<String> requestedCapabilities,
            List<IntentConstraint> hardFilters,
            List<IntentConstraint> softPreferences,
            SubjectMeasurements subjectMeasurements
    ) {
        return new EffectiveDemand(VERSION, rawQuery, requestType, requestedCapabilities,
                hardFilters, softPreferences, subjectMeasurements);
    }

    /** Returns every active hard or soft condition for a field. */
    public List<IntentConstraint> constraints(String field) {
        return Stream.concat(hardFilters.stream(), softPreferences.stream())
                .filter(constraint -> constraint.field().equals(field))
                .toList();
    }

    /**
     * Convenience lookup for scalar consumers; hard constraints take precedence and only the first value is returned.
     */
    public Optional<String> value(String field) {
        return constraints(field).stream()
                .flatMap(constraint -> constraint.values().stream())
                .findFirst();
    }

    /** 解析硬 EQUALS 约束的唯一交集值；冲突或歧义时拒绝放宽查询。 */
    public Optional<String> hardValue(String field) {
        List<IntentConstraint> matching = hardFilters.stream()
                .filter(constraint -> constraint.field().equals(field))
                .toList();
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        if (matching.stream().anyMatch(constraint -> constraint.operator() != ConstraintOperator.EQUALS)) {
            throw new IllegalStateException("hard field " + field + " requires EQUALS constraints");
        }
        Set<String> intersection = new LinkedHashSet<>(matching.getFirst().values());
        if (intersection.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalStateException("hard field " + field + " contains a blank canonical value");
        }
        matching.stream().skip(1).forEach(constraint -> intersection.retainAll(constraint.values()));
        if (intersection.isEmpty()) {
            throw new IllegalStateException("conflicting hard EQUALS constraints for field " + field);
        }
        if (intersection.size() != 1) {
            throw new IllegalStateException("ambiguous hard EQUALS constraints for field " + field);
        }
        return Optional.of(intersection.iterator().next());
    }

    /** 解析所有硬 MAX 值并取最严格的非负整数上限；非法约束直接拒绝查询。 */
    public Optional<Integer> hardInteger(String field) {
        List<IntentConstraint> matching = hardFilters.stream()
                .filter(constraint -> constraint.field().equals(field))
                .toList();
        if (matching.isEmpty()) {
            return Optional.empty();
        }
        if (matching.stream().anyMatch(constraint -> constraint.operator() != ConstraintOperator.MAX)) {
            throw new IllegalStateException("hard integer field " + field + " requires MAX constraints");
        }
        int strictest = Integer.MAX_VALUE;
        for (IntentConstraint constraint : matching) {
            for (String value : constraint.values()) {
                final int parsed;
                try {
                    parsed = Integer.parseInt(value);
                } catch (NumberFormatException exception) {
                    throw new IllegalStateException(
                            "hard integer field " + field + " contains an invalid value", exception);
                }
                if (parsed < 0) {
                    throw new IllegalStateException(
                            "hard integer field " + field + " requires a non-negative value");
                }
                strictest = Math.min(strictest, parsed);
            }
        }
        return Optional.of(strictest);
    }

    /** Creates a new snapshot after derived-preference recalculation without mutating this one. */
    public EffectiveDemand withSoftPreferences(List<IntentConstraint> preferences) {
        return new EffectiveDemand(version, rawQuery, requestType, requestedCapabilities,
                hardFilters, preferences, subjectMeasurements);
    }

    private static void requireStrength(
            List<IntentConstraint> constraints,
            ConstraintStrength expected,
            String collectionName
    ) {
        if (constraints.stream().anyMatch(constraint -> constraint.strength() != expected)) {
            throw new IllegalArgumentException(collectionName + " contains a constraint with the wrong strength");
        }
    }
}
