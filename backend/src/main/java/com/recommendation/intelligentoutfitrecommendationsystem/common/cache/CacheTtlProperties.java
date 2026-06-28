package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis 缓存 TTL 配置。
 *
 * TTL 统一放在配置类中，避免业务 Service 写死过期时间；随机抖动用于降低大量缓存
 * 同时过期后集中回源 MySQL 的风险。
 */
@ConfigurationProperties(prefix = "app.cache.ttl")
public class CacheTtlProperties {

    private int productDetailMinutes = 60;

    private int productDetailJitterMinutes = 5;

    private int userProfileMinutes = 20;

    private int userProfileJitterMinutes = 5;

    private int recommendationCandidatesMinutes = 10;

    private int recommendationCandidatesJitterMinutes = 5;

    private int assistantRateLimitSeconds = 60;

    public int getProductDetailMinutes() {
        return productDetailMinutes;
    }

    public void setProductDetailMinutes(int productDetailMinutes) {
        this.productDetailMinutes = productDetailMinutes;
    }

    public int getProductDetailJitterMinutes() {
        return productDetailJitterMinutes;
    }

    public void setProductDetailJitterMinutes(int productDetailJitterMinutes) {
        this.productDetailJitterMinutes = productDetailJitterMinutes;
    }

    public int getUserProfileMinutes() {
        return userProfileMinutes;
    }

    public void setUserProfileMinutes(int userProfileMinutes) {
        this.userProfileMinutes = userProfileMinutes;
    }

    public int getUserProfileJitterMinutes() {
        return userProfileJitterMinutes;
    }

    public void setUserProfileJitterMinutes(int userProfileJitterMinutes) {
        this.userProfileJitterMinutes = userProfileJitterMinutes;
    }

    public int getRecommendationCandidatesMinutes() {
        return recommendationCandidatesMinutes;
    }

    public void setRecommendationCandidatesMinutes(int recommendationCandidatesMinutes) {
        this.recommendationCandidatesMinutes = recommendationCandidatesMinutes;
    }

    public int getRecommendationCandidatesJitterMinutes() {
        return recommendationCandidatesJitterMinutes;
    }

    public void setRecommendationCandidatesJitterMinutes(int recommendationCandidatesJitterMinutes) {
        this.recommendationCandidatesJitterMinutes = recommendationCandidatesJitterMinutes;
    }

    public int getAssistantRateLimitSeconds() {
        return assistantRateLimitSeconds;
    }

    public void setAssistantRateLimitSeconds(int assistantRateLimitSeconds) {
        this.assistantRateLimitSeconds = assistantRateLimitSeconds;
    }

    /**
     * 商品详情缓存 TTL。
     *
     * 商品详情读多写少，可以比推荐候选保存更久；抖动用于错开大量 key 的过期时间。
     */
    public Duration productDetailTtl() {
        return withJitter(productDetailMinutes, productDetailJitterMinutes);
    }

    /**
     * 用户基础画像缓存 TTL。
     *
     * 用户修改资料后会主动删除缓存，因此正常读取可以使用中等长度 TTL。
     */
    public Duration userProfileTtl() {
        return withJitter(userProfileMinutes, userProfileJitterMinutes);
    }

    /**
     * 推荐候选缓存 TTL。
     *
     * 推荐候选受价格、库存、标签等条件影响，第一版使用短 TTL 自动过期，避免复杂精确删除。
     */
    public Duration recommendationCandidatesTtl() {
        return withJitter(recommendationCandidatesMinutes, recommendationCandidatesJitterMinutes);
    }

    /**
     * AI 限流计数 key TTL。
     *
     * 限流窗口按分钟统计，key 只需要保存一个短窗口即可。
     */
    public Duration assistantRateLimitTtl() {
        return Duration.ofSeconds(Math.max(assistantRateLimitSeconds, 1));
    }

    private Duration withJitter(int baseMinutes, int jitterMinutes) {
        int safeBaseMinutes = Math.max(baseMinutes, 1);
        int safeJitterMinutes = Math.max(jitterMinutes, 0);
        int randomJitter = safeJitterMinutes == 0
                ? 0
                : ThreadLocalRandom.current().nextInt(safeJitterMinutes + 1);
        return Duration.ofMinutes(safeBaseMinutes + randomJitter);
    }
}
