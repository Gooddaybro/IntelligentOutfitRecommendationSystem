package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

/**
 * Redis key 命名集中入口。
 *
 * 业务 Service 只表达自己要缓存的业务对象，不直接散落 key 字符串，
 * 方便后续统一排查 Redis 数据和调整 key 版本。
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
    }

    /**
     * 商品详情缓存 key。
     *
     * 商品详情按 SPU 维度读取，key 中只需要 spuId 即可定位唯一商品事实。
     */
    public static String productDetail(Long spuId) {
        return "product:detail:" + spuId;
    }

    /**
     * 用户基础画像缓存 key。
     *
     * 用户画像属于登录用户个人上下文，按 userId 隔离，避免不同用户缓存串读。
     */
    public static String userProfile(Long userId) {
        return "user:profile:" + userId;
    }

    /**
     * 推荐候选缓存 key。
     *
     * 推荐候选由多个筛选条件共同决定，外部传入的是标准化查询条件的 hash，而不是原始查询文本。
     */
    public static String recommendationCandidates(String queryHash) {
        return "product:recommendation-candidates:" + queryHash;
    }

    /**
     * AI 用户级限流 key。
     *
     * minuteBucket 使用分钟级时间桶，让 Redis 可以对每个用户每分钟的 AI 请求数单独计数。
     */
    public static String assistantUserRateLimit(Long userId, long minuteBucket) {
        return "assistant:rate-limit:user:" + userId + ":" + minuteBucket;
    }
}
