package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicitly maps the complete supported cross-service enum set to existing Java domain values. */
public class DemandIntentNormalizer {

    private static final Map<String, String> GENDERS = Map.of(
            "MALE", "male", "FEMALE", "female", "UNISEX", "unisex"
    );
    private static final Map<String, String> CATEGORIES = Map.of(
            "OUTERWEAR", "外套", "SKIRT", "半身裙", "SHORTS", "短裤",
            "SHIRT", "衬衫", "TOP", "上衣", "PANTS", "裤子"
    );
    private static final Map<String, String> SCENES = Map.of(
            "COMMUTE", "commute", "DATE", "date", "CAMPUS", "campus",
            "DAILY", "daily", "TRAVEL", "travel", "SPORT", "sport"
    );
    private static final Map<String, String> STYLES = Map.of(
            "MATURE", "mature", "RUGGED", "rugged", "MINIMAL", "minimal", "CASUAL", "casual"
    );
    private static final Map<String, String> FIT_PREFERENCES = Map.of(
            "RELAXED", "relaxed", "REGULAR", "regular", "SLIM", "slim"
    );
    private static final Map<String, String> ATTRIBUTES = Map.ofEntries(
            Map.entry("TALLER", "显高"), Map.entry("SLIMMING", "显瘦"),
            Map.entry("COVERING", "遮肉"), Map.entry("HIGH_WAIST", "高腰"),
            Map.entry("DRAPED", "垂顺"), Map.entry("STRUCTURED", "挺括"),
            Map.entry("WARM", "保暖"), Map.entry("THICK", "厚款"),
            Map.entry("AFFORDABLE", "平价")
    );

    public String gender(String value) {
        return (String) canonicalValue("targetGender", value);
    }

    public String category(String value) {
        return (String) canonicalValue("category", value);
    }

    public Map<String, Object> toCanonicalDemand(
            com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent intent
    ) {
        if (intent == null) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        putReverse(result, "targetGender", GENDERS, intent.targetGender());
        putReverse(result, "category", CATEGORIES, intent.category());
        putReverseList(result, "scene", SCENES, intent.scene());
        putReverseList(result, "style", STYLES, intent.style());
        putReverseList(result, "fitPreferences", FIT_PREFERENCES, intent.fitPreferences());
        if (intent.budgetMax() != null) {
            result.put("budgetMax", intent.budgetMax());
        }
        putReverseList(result, "attributes", ATTRIBUTES, intent.attributes());
        return Map.copyOf(result);
    }

    public Object canonicalValue(String slot, Object value) {
        if (value == null) {
            return null;
        }
        if ("budgetMax".equals(slot)) {
            return value instanceof Integer ? value : null;
        }
        if (value instanceof String stringValue) {
            return mapping(slot).get(stringValue);
        }
        if (value instanceof List<?> values && isListSlot(slot) && !values.isEmpty()) {
            List<String> normalized = values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .map(mapping(slot)::get)
                    .toList();
            return normalized.size() == values.size() && normalized.stream().noneMatch(item -> item == null)
                    ? normalized : null;
        }
        return null;
    }

    public boolean evidenceCompatible(String slot, Object canonicalValue, String evidenceText) {
        if (evidenceText == null || evidenceText.isBlank() || canonicalValue == null) {
            return false;
        }
        if (canonicalValue instanceof Integer budget) {
            return evidenceText.matches(".*\\b" + budget + "\\b.*");
        }
        if (canonicalValue instanceof List<?> values) {
            return values.stream().anyMatch(value -> evidenceCompatible(slot, value, evidenceText));
        }
        String value = String.valueOf(canonicalValue);
        return switch (slot) {
            case "targetGender" -> "female".equals(value)
                    ? containsAny(evidenceText, "女", "她", "妈妈", "姐妹")
                    : "male".equals(value) && containsAny(evidenceText, "男", "他", "爸爸", "兄弟");
            case "category" -> evidenceText.contains(value)
                    || ("半身裙".equals(value) && evidenceText.contains("裙"));
            case "scene", "style", "fitPreferences" -> evidenceText.contains(chineseSoftValue(value));
            case "attributes" -> evidenceText.contains(value);
            default -> false;
        };
    }

    private Map<String, String> mapping(String slot) {
        return switch (slot) {
            case "targetGender" -> GENDERS;
            case "category" -> CATEGORIES;
            case "scene" -> SCENES;
            case "style" -> STYLES;
            case "fitPreferences" -> FIT_PREFERENCES;
            case "attributes" -> ATTRIBUTES;
            default -> Map.of();
        };
    }

    private void putReverse(Map<String, Object> result, String slot, Map<String, String> values, String domainValue) {
        values.entrySet().stream()
                .filter(entry -> entry.getValue().equals(domainValue))
                .map(Map.Entry::getKey)
                .findFirst()
                .ifPresent(value -> result.put(slot, value));
    }

    private void putReverseList(
            Map<String, Object> result,
            String slot,
            Map<String, String> values,
            List<String> domainValues
    ) {
        if (domainValues == null || domainValues.isEmpty()) {
            return;
        }
        List<String> canonical = domainValues.stream()
                .map(value -> values.entrySet().stream()
                        .filter(entry -> entry.getValue().equals(value))
                        .map(Map.Entry::getKey)
                        .findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (!canonical.isEmpty()) {
            result.put(slot, canonical);
        }
    }

    private boolean isListSlot(String slot) {
        return "scene".equals(slot) || "style".equals(slot) || "fitPreferences".equals(slot)
                || "attributes".equals(slot);
    }

    private String chineseSoftValue(String value) {
        return switch (value) {
            case "commute" -> "通勤";
            case "date" -> "约会";
            case "campus" -> "校园";
            case "daily" -> "日常";
            case "travel" -> "旅行";
            case "sport" -> "运动";
            case "mature" -> "成熟";
            case "rugged" -> "硬朗";
            case "minimal" -> "简约";
            case "casual" -> "休闲";
            case "relaxed" -> "轻松";
            case "regular" -> "合身";
            case "slim" -> "修身";
            default -> value;
        };
    }

    private boolean containsAny(String text, String... signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return true;
            }
        }
        return false;
    }
}
