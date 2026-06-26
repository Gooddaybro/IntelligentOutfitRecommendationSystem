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

    public static String productDetail(Long spuId) {
        return "product:detail:" + spuId;
    }
}
