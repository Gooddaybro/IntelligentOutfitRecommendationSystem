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
        List<String> attributes,
        List<String> fitPreferences
) {
    public Map<String, Object> presentSlots() {
        Map<String, Object> result = new LinkedHashMap<>();
        putIfPresent(result, "targetGender", targetGender);
        putIfPresent(result, "category", category);
        putIfPresent(result, "scene", scene);
        putIfPresent(result, "style", style);
        putIfPresent(result, "budgetMax", budgetMax);
        putIfPresent(result, "attributes", attributes);
        putIfPresent(result, "fitPreferences", fitPreferences);
        return Map.copyOf(result);
    }

    /** Creates the original parser shape while leaving v2 fit preferences absent. */
    public LlmDemandSlots(
            String targetGender,
            String category,
            List<String> scene,
            List<String> style,
            Integer budgetMax,
            List<String> attributes
    ) {
        this(targetGender, category, scene, style, budgetMax, attributes, null);
    }

    private void putIfPresent(Map<String, Object> result, String slot, Object value) {
        if (value != null) {
            result.put(slot, value);
        }
    }
}
