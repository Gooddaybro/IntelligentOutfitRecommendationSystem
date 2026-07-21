package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Immutable Java-authoritative demand after merging and derivation.
 * Candidate filtering and downstream recommendation contracts consume this snapshot, not turn patches.
 */
public record EffectiveDemand(
        String version,
        @JsonIgnore String rawQuery,
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
