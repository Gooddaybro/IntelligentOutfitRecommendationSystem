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
    public static final String VERSION = "demand-intent-v1";
    public static final String SOURCE_JAVA_RULE = "java-rule";

    public DemandIntent {
        version = isBlank(version) ? VERSION : version.trim();
        source = isBlank(source) ? SOURCE_JAVA_RULE : source.trim();
        rawQuery = rawQuery == null ? "" : rawQuery;
        scene = immutableList(scene);
        style = immutableList(style);
        attributes = immutableList(attributes);
        hardFilters = immutableList(hardFilters);
        softPreferences = immutableList(softPreferences);
        confidence = confidence == null ? BigDecimal.ZERO : confidence;
        missingSlots = immutableList(missingSlots);
    }

    public static DemandIntent empty(String rawQuery) {
        return new DemandIntent(
                VERSION,
                SOURCE_JAVA_RULE,
                rawQuery,
                null,
                null,
                List.of(),
                List.of(),
                null,
                List.of(),
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
