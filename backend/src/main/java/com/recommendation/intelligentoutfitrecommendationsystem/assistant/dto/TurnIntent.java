package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable current-message operations passed to the intent merger.
 * It deliberately carries no inherited state, preventing old evidence from appearing current.
 */
public record TurnIntent(
        String turnId,
        String rawQuery,
        Map<String, IntentConstraint> scalarReplacements,
        List<IntentConstraint> explicitAdditions,
        List<IntentConstraint> explicitRemovals,
        Set<String> clearFields,
        String requestType,
        List<String> requestedCapabilities,
        SubjectMeasurements subjectMeasurements
) {
    public TurnIntent {
        if (turnId == null || turnId.isBlank()) {
            throw new IllegalArgumentException("turn id is required");
        }
        rawQuery = rawQuery == null ? "" : rawQuery;
        scalarReplacements = scalarReplacements == null ? Map.of() : Map.copyOf(scalarReplacements);
        if (scalarReplacements.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equals(entry.getValue().field()))) {
            throw new IllegalArgumentException("scalar replacement keys must match constraint fields");
        }
        explicitAdditions = explicitAdditions == null ? List.of() : List.copyOf(explicitAdditions);
        explicitRemovals = explicitRemovals == null ? List.of() : List.copyOf(explicitRemovals);
        clearFields = clearFields == null ? Set.of() : Set.copyOf(clearFields);
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
    }
}
