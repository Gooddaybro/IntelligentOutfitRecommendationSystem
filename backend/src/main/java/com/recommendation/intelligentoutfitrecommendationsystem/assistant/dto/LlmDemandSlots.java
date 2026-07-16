package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sparse canonical slots returned by the Python parser. */
public record LlmDemandSlots(
        String targetGender,
        String category,
        List<String> scene,
        List<String> style,
        Integer budgetMax,
        List<String> attributes
) {
    public Map<String, Object> presentSlots() {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "targetGender", targetGender);
        putIfPresent(result, "category", category);
        putIfPresent(result, "scene", scene);
        putIfPresent(result, "style", style);
        putIfPresent(result, "budgetMax", budgetMax);
        putIfPresent(result, "attributes", attributes);
        return Map.copyOf(result);
    }

    private void putIfPresent(Map<String, Object> result, String slot, Object value) {
        if (value != null) {
            result.put(slot, value);
        }
    }
}
