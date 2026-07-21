package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Publishes bounded recommendation counts and reason codes without exposing queries or body measurements.
 *
 * <p>The fixed reason-code vocabulary keeps both the public contract and metric labels low-cardinality.</p>
 */
public record RecommendationDiagnostics(
        int javaCandidateCount,
        int pythonSelectedCount,
        int javaAcceptedCount,
        RecommendationStatus status,
        List<String> reasonCodes
) {
    private static final Set<String> ALLOWED_REASON_CODES = Set.of(
            "STALE_DERIVED_CONSTRAINT_REMOVED",
            "PYTHON_REJECTED_ALL",
            "JAVA_DISCARDED_ALL_REFS",
            "NO_JAVA_CANDIDATES",
            "DEPENDENCY_FAILED"
    );

    public RecommendationDiagnostics {
        if (javaCandidateCount < 0 || pythonSelectedCount < 0 || javaAcceptedCount < 0) {
            throw new IllegalArgumentException("recommendation diagnostic counts must be non-negative");
        }
        status = Objects.requireNonNull(status, "status");
        reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
        if (!ALLOWED_REASON_CODES.containsAll(reasonCodes)) {
            throw new IllegalArgumentException("recommendation diagnostics contain an unsupported reason code");
        }
    }
}
