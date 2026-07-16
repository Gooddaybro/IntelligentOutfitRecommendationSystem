package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Explicitly maps cross-service canonical enums to existing Java domain values. */
public class DemandIntentNormalizer {

    private static final Map<String, String> GENDERS = Map.of(
            "MALE", "male", "FEMALE", "female", "UNISEX", "unisex"
    );
    private static final Map<String, String> CATEGORIES = Map.of(
            "OUTERWEAR", "外套", "SKIRT", "半身裙", "TOP", "上衣", "PANTS", "裤子"
    );

    public String gender(String value) {
        return value == null ? null : GENDERS.get(value);
    }

    public String category(String value) {
        return value == null ? null : CATEGORIES.get(value);
    }

    public List<String> softValues(List<String> values) {
        return values == null ? List.of() : values.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
    }
}
