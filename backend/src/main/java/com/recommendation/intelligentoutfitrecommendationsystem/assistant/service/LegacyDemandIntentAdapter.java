package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Migrates persisted v2 demand snapshots into v3 without inventing user provenance.
 * Legacy scalars remain filters, while every legacy multi-value preference is deliberately soft and unprovenanced.
 */
public class LegacyDemandIntentAdapter {

    /**
     * Produces deterministic constraint identifiers so repeated reads of an unmigrated row are idempotent.
     *
     * @param legacy stored v2 snapshot
     * @return equivalent v3 effective state with conservative provenance
     */
    public EffectiveDemand adapt(DemandIntent legacy) {
        if (legacy == null) {
            return EffectiveDemand.v3("", null, List.of(), List.of(), List.of(), null);
        }
        ConstraintOrigin scalarOrigin = scalarOrigin(legacy.source());
        List<IntentConstraint> hard = new ArrayList<>();
        add(hard, "targetGender", canonical("targetGender", legacy.targetGender()),
                ConstraintStrength.HARD, scalarOrigin);
        add(hard, "category", canonical("category", legacy.category()), ConstraintStrength.HARD, scalarOrigin);
        add(hard, "season", canonical("season", legacy.season()), ConstraintStrength.HARD, scalarOrigin);
        add(hard, "budgetMax", legacy.budgetMax() == null ? null : String.valueOf(legacy.budgetMax()),
                ConstraintStrength.HARD, scalarOrigin);

        List<IntentConstraint> soft = new ArrayList<>();
        addList(soft, "scene", legacy.scene());
        addList(soft, "style", legacy.style());
        addList(soft, "fitPreferences", legacy.fitPreferences());
        Map<String, List<String>> attributes = new LinkedHashMap<>();
        for (String attribute : legacy.attributes()) {
            Attribute converted = attribute(attribute);
            attributes.computeIfAbsent(converted.field(), ignored -> new ArrayList<>()).add(converted.value());
        }
        attributes.forEach((field, values) -> add(soft, field, values.stream().distinct().toList(),
                ConstraintStrength.SOFT, ConstraintOrigin.LEGACY_UNPROVENANCED));
        return EffectiveDemand.v3(legacy.rawQuery(), legacy.requestType(), legacy.requestedCapabilities(),
                hard, soft, legacy.subjectMeasurements());
    }

    private void addList(List<IntentConstraint> target, String field, List<String> values) {
        List<String> canonical = values.stream()
                .map(value -> canonical(field, value))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        add(target, field, canonical, ConstraintStrength.SOFT, ConstraintOrigin.LEGACY_UNPROVENANCED);
    }

    private void add(
            List<IntentConstraint> target,
            String field,
            String value,
            ConstraintStrength strength,
            ConstraintOrigin origin
    ) {
        if (value != null) {
            add(target, field, List.of(value), strength, origin);
        }
    }

    private void add(
            List<IntentConstraint> target,
            String field,
            List<String> values,
            ConstraintStrength strength,
            ConstraintOrigin origin
    ) {
        if (values.isEmpty()) {
            return;
        }
        String semantic = field + "|" + String.join(",", values) + "|" + strength + "|" + origin;
        String id = "legacy-" + UUID.nameUUIDFromBytes(semantic.getBytes(StandardCharsets.UTF_8));
        ConstraintOperator operator = strength == ConstraintStrength.HARD
                ? ("budgetMax".equals(field) ? ConstraintOperator.MAX : ConstraintOperator.EQUALS)
                : ConstraintOperator.CONTAINS;
        target.add(new IntentConstraint(id, field, operator,
                values, strength, origin, null, null, "ACTIVE_DEMAND", null));
    }

    private ConstraintOrigin scalarOrigin(String source) {
        if (source == null) {
            return ConstraintOrigin.LEGACY_UNPROVENANCED;
        }
        return switch (source.trim().toLowerCase(Locale.ROOT)) {
            case "profile" -> ConstraintOrigin.PROFILE;
            case "user", "user-explicit", "current-message" -> ConstraintOrigin.USER_EXPLICIT;
            default -> ConstraintOrigin.LEGACY_UNPROVENANCED;
        };
    }

    private String canonical(String field, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (field) {
            case "targetGender" -> switch (normalized) {
                case "male", "\u7537", "\u7537\u6027", "\u7537\u751f" -> "MALE";
                case "female", "\u5973", "\u5973\u6027", "\u5973\u751f" -> "FEMALE";
                case "unisex" -> "UNISEX";
                default -> value.trim().toUpperCase(Locale.ROOT);
            };
            case "season" -> switch (normalized) {
                case "spring", "\u6625", "\u6625\u5929", "\u6625\u5b63" -> "SPRING";
                case "summer", "\u590f", "\u590f\u5929", "\u590f\u5b63" -> "SUMMER";
                case "autumn", "fall", "\u79cb", "\u79cb\u5929", "\u79cb\u5b63" -> "AUTUMN";
                case "winter", "\u51ac", "\u51ac\u5929", "\u51ac\u5b63" -> "WINTER";
                default -> value.trim().toUpperCase(Locale.ROOT);
            };
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private Attribute attribute(String value) {
        String normalized = value == null ? "" : value.trim();
        if (Set.of("\u4fdd\u6696", "warm").contains(normalized.toLowerCase(Locale.ROOT))) {
            return new Attribute("thermal", "WARM");
        }
        if (Set.of("\u539a\u6b3e", "thick").contains(normalized.toLowerCase(Locale.ROOT))) {
            return new Attribute("thickness", "THICK");
        }
        return new Attribute("attributes", normalized.toUpperCase(Locale.ROOT));
    }

    private record Attribute(String field, String value) {
    }
}
