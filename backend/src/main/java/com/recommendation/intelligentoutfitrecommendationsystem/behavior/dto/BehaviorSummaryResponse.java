package com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto;

import java.util.List;

/**
 * 当前用户近期行为摘要。
 *
 * 该响应用于本地调试接口和 AI 上下文装配，只暴露聚合后的轻量偏好，不返回行为流水。
 */
public record BehaviorSummaryResponse(
        List<Long> recentInterestSpuIds,
        List<Long> recentCartSpuIds,
        List<Long> recentPurchasedSpuIds,
        List<String> preferredCategories,
        List<String> preferredStyles,
        List<Long> recentExposedSpuIds
) {
    public BehaviorSummaryResponse {
        recentInterestSpuIds = copyOrEmpty(recentInterestSpuIds);
        recentCartSpuIds = copyOrEmpty(recentCartSpuIds);
        recentPurchasedSpuIds = copyOrEmpty(recentPurchasedSpuIds);
        preferredCategories = copyOrEmpty(preferredCategories);
        preferredStyles = copyOrEmpty(preferredStyles);
        recentExposedSpuIds = copyOrEmpty(recentExposedSpuIds);
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
