package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Java 最终推荐决策的稳定状态集合。
 *
 * 该枚举区分完整匹配、部分搭配、可浏览降级、无候选与决策失败，避免跨服务调用方猜测字符串语义。
 */
public enum RecommendationStatus {
    STRONG_MATCH,
    PARTIAL_MATCH,
    BROWSE_FALLBACK,
    EMPTY,
    FAILED
}
