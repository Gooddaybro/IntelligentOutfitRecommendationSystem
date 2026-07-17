package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** A validated, single-turn update to the conversation's effective shopping demand. */
public record DemandIntentPatch(
        String action,
        String rawQuery,
        String requestType,
        List<String> requestedCapabilities,
        String targetGender,
        boolean clearTargetGender,
        String category,
        String season,
        List<String> scene,
        List<String> style,
        List<String> fitPreferences,
        Integer budgetMax,
        List<String> attributes,
        SubjectMeasurements subjectMeasurements,
        boolean clearSubjectMeasurements
) {
    public DemandIntentPatch {
        action = action == null || action.isBlank() ? "merge" : action;
        rawQuery = rawQuery == null ? "" : rawQuery;
        requestType = requestType == null || requestType.isBlank() ? null : requestType;
        requestedCapabilities = requestedCapabilities == null ? List.of() : List.copyOf(requestedCapabilities);
        scene = scene == null ? List.of() : List.copyOf(scene);
        style = style == null ? List.of() : List.copyOf(style);
        fitPreferences = fitPreferences == null ? List.of() : List.copyOf(fitPreferences);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /** Creates a v1-shaped patch while defaulting all v2-only fields for source compatibility. */
    public DemandIntentPatch(
            String action,
            String rawQuery,
            String targetGender,
            boolean clearTargetGender,
            String category,
            List<String> scene,
            List<String> style,
            Integer budgetMax,
            List<String> attributes
    ) {
        this(action, rawQuery, null, List.of(), targetGender, clearTargetGender, category, null,
                scene, style, List.of(), budgetMax, attributes, null, false);
    }
}
