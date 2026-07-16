package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/** Sparse canonical slots returned by the Python parser. */
public record LlmDemandSlots(
        String targetGender,
        String category,
        List<String> scene,
        List<String> style,
        Integer budgetMax,
        List<String> attributes
) {
}
