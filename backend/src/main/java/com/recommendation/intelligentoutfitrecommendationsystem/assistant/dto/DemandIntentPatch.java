package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** A validated, single-turn update to the conversation's effective shopping demand. */
public record DemandIntentPatch(
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
    public DemandIntentPatch {
        action = action == null || action.isBlank() ? "merge" : action;
        rawQuery = rawQuery == null ? "" : rawQuery;
        scene = scene == null ? List.of() : List.copyOf(scene);
        style = style == null ? List.of() : List.copyOf(style);
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }
}
