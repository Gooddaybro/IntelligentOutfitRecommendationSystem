package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Measurements for the person currently being advised, scoped to the active demand only.
 *
 * <p>This value is deliberately separate from the signed-in user's persisted body profile.</p>
 */
public record SubjectMeasurements(
        BigDecimal heightCm,
        BigDecimal weightKg,
        String originalText,
        String normalizedFrom,
        String subject,
        String scope,
        String source
) {
    private static final Set<String> SUBJECTS = Set.of("SELF", "OTHER", "UNKNOWN");

    public SubjectMeasurements {
        originalText = originalText == null ? "" : originalText.trim();
        normalizedFrom = normalizedFrom == null ? null : normalizedFrom.trim();
        subject = SUBJECTS.contains(subject) ? subject : "UNKNOWN";
        scope = scope == null || scope.isBlank() ? "ACTIVE_DEMAND" : scope.trim();
        source = source == null || source.isBlank() ? "CURRENT_MESSAGE" : source.trim();
    }
}
