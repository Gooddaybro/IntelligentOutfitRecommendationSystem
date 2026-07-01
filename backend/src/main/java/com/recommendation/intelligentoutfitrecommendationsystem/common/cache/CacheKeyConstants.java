package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

/**
 * Centralizes Redis key formats so cache readers and invalidators share the same business namespace.
 */
public final class CacheKeyConstants {
    private CacheKeyConstants() {
    }

    public static String productDetail(Long spuId) {
        return "product:detail:" + spuId;
    }

    public static String productSearch(String normalizedKeyword) {
        return "product:search:" + normalizedKeyword;
    }

    public static String userProfile(Long userId) {
        return "user:profile:" + userId;
    }

    public static String recommendationCandidates(String queryHash) {
        return "product:recommendation-candidates:" + queryHash;
    }

    public static String assistantUserRateLimit(Long userId, long minuteBucket) {
        return "assistant:rate-limit:user:" + userId + ":" + minuteBucket;
    }
}
