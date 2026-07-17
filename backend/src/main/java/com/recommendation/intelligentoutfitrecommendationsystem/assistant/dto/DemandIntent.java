package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Java 统一解析出的用户穿搭需求意图。
 *
 * 后端用 hardFilters 做商品候选过滤，Python 和前端只消费同一个解析结果，避免多端各自猜筛选条件。
 */
public record DemandIntent(
        String version,
        String source,
        String rawQuery,
        String requestType,
        List<String> requestedCapabilities,
        String targetGender,
        String category,
        String season,
        List<String> scene,
        List<String> style,
        List<String> fitPreferences,
        Integer budgetMax,
        List<String> attributes,
        SubjectMeasurements subjectMeasurements,
        List<String> hardFilters,
        List<String> softPreferences,
        BigDecimal confidence,
        List<String> missingSlots
) {
    public static final String VERSION = "demand-intent-v2";
    public static final String SOURCE_JAVA_RULE = "java-rule";

    public DemandIntent {
        version = isBlank(version) ? VERSION : version.trim();
        source = isBlank(source) ? SOURCE_JAVA_RULE : source.trim();
        rawQuery = rawQuery == null ? "" : rawQuery;
        requestType = isBlank(requestType) ? null : requestType.trim();
        requestedCapabilities = immutableList(requestedCapabilities);
        season = isBlank(season) ? null : season.trim();
        scene = immutableList(scene);
        style = immutableList(style);
        fitPreferences = immutableList(fitPreferences);
        attributes = immutableList(attributes);
        hardFilters = immutableList(hardFilters);
        softPreferences = immutableList(softPreferences);
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        missingSlots = immutableList(missingSlots);
    }

    /** Creates a v1-shaped intent while defaulting all v2-only fields for source compatibility. */
    public DemandIntent(
            String version,
            String source,
            String rawQuery,
            String targetGender,
            String category,
            List<String> scene,
            List<String> style,
            Integer budgetMax,
            List<String> attributes,
            List<String> hardFilters,
            List<String> softPreferences,
            BigDecimal confidence,
            List<String> missingSlots
    ) {
        this(version, source, rawQuery, null, List.of(), targetGender, category, null, scene, style,
                List.of(), budgetMax, attributes, null, hardFilters, softPreferences, confidence, missingSlots);
    }

    public static DemandIntent empty(String rawQuery) {
        return new DemandIntent(
                VERSION,
                SOURCE_JAVA_RULE,
                rawQuery,
                null,
                List.of(),
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                List.of(),
                List.of(),
                BigDecimal.ZERO,
                List.of()
        );
    }

    private static List<String> immutableList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
