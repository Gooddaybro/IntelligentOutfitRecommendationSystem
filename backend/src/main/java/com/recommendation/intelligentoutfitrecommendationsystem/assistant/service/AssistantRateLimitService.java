package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheKeyConstants;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * AI 聊天接口限流服务。
 *
 * AI 调用比普通商品查询更慢且有模型成本，因此在进入 Python 服务之前用 Redis 做用户级分钟窗口限流。
 */
@Service
public class AssistantRateLimitService {

    private final RedisCacheService redisCacheService;
    private final CacheTtlProperties cacheTtlProperties;
    private final int userLimitPerMinute;

    public AssistantRateLimitService(
            RedisCacheService redisCacheService,
            CacheTtlProperties cacheTtlProperties,
            @Value("${app.ai.rate-limit.user-per-minute:10}") int userLimitPerMinute
    ) {
        this.redisCacheService = redisCacheService;
        this.cacheTtlProperties = cacheTtlProperties;
        this.userLimitPerMinute = userLimitPerMinute;
    }

    /**
     * 校验用户是否仍在 AI 每分钟请求阈值内。
     *
     * Redis 不可用时采用失败放行策略，避免缓存基础设施故障直接阻断核心问答功能。
     */
    public void assertAllowed(Long userId) {
        long minuteBucket = Instant.now().getEpochSecond() / 60;
        String key = CacheKeyConstants.assistantUserRateLimit(userId, minuteBucket);
        var count = redisCacheService.incrementWithTtl(key, cacheTtlProperties.assistantRateLimitTtl());
        if (count.isPresent() && count.get() > userLimitPerMinute) {
            throw new RateLimitExceededException("AI requests are too frequent. Please try again later.");
        }
    }
}
