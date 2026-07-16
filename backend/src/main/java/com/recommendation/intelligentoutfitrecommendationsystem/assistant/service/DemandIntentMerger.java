package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Applies one validated patch without re-reading or voting over chat history. */
public class DemandIntentMerger {

    public DemandIntent merge(DemandIntent previous, DemandIntentPatch patch) {
        if ("compare".equals(patch.action())) {
            return previous == null ? DemandIntent.empty(patch.rawQuery()) : previous;
        }
        if ("reset".equals(patch.action())) {
            return DemandIntent.empty(patch.rawQuery());
        }

        DemandIntent base = previous == null ? DemandIntent.empty(patch.rawQuery()) : previous;
        String gender = patch.clearTargetGender()
                ? null
                : patch.targetGender() == null ? base.targetGender() : patch.targetGender();
        String category = patch.category() == null ? base.category() : patch.category();
        List<String> scene = mergeLists(base.scene(), patch.scene());
        List<String> style = mergeLists(base.style(), patch.style());
        Integer budget = patch.budgetMax() == null ? base.budgetMax() : patch.budgetMax();
        List<String> attributes = mergeLists(base.attributes(), patch.attributes());
        List<String> hardFilters = hardFilters(gender, category, budget);
        List<String> softPreferences = softPreferences(scene, style, attributes);

        return new DemandIntent(
                DemandIntent.VERSION,
                DemandIntent.SOURCE_JAVA_RULE,
                patch.rawQuery(),
                gender,
                category,
                scene,
                style,
                budget,
                attributes,
                hardFilters,
                softPreferences,
                hardFilters.isEmpty() && softPreferences.isEmpty() ? BigDecimal.ZERO : new BigDecimal("0.80"),
                List.of()
        );
    }

    private List<String> mergeLists(List<String> previous, List<String> patch) {
        LinkedHashSet<String> values = new LinkedHashSet<>(previous);
        values.addAll(patch);
        return List.copyOf(values);
    }

    private List<String> hardFilters(String gender, String category, Integer budget) {
        List<String> filters = new ArrayList<>();
        if (gender != null) {
            filters.add("targetGender");
        }
        if (category != null) {
            filters.add("category");
        }
        if (budget != null) {
            filters.add("budgetMax");
        }
        return List.copyOf(filters);
    }

    private List<String> softPreferences(List<String> scene, List<String> style, List<String> attributes) {
        List<String> preferences = new ArrayList<>();
        if (!scene.isEmpty()) {
            preferences.add("scene");
        }
        if (!style.isEmpty()) {
            preferences.add("style");
        }
        if (!attributes.isEmpty()) {
            preferences.add("attributes");
        }
        return List.copyOf(preferences);
    }
}
