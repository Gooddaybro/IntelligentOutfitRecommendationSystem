package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Binds Redis TTL settings for cache-aside reads and AI request rate-limit counters.
 */
@ConfigurationProperties(prefix = "app.cache.ttl")
public class CacheTtlProperties {
    private long productDetailMinutes = 60;
    private long productDetailJitterMinutes = 5;
    private long productSearchMinutes = 5;
    private long productSearchJitterMinutes = 1;
    private long userProfileMinutes = 20;
    private long userProfileJitterMinutes = 5;
    private long recommendationCandidatesMinutes = 10;
    private long recommendationCandidatesJitterMinutes = 5;
    private long assistantRateLimitSeconds = 60;

    public Duration productDetailTtl() {
        return minutesWithJitter(productDetailMinutes, productDetailJitterMinutes);
    }

    public Duration productSearchTtl() {
        return minutesWithJitter(productSearchMinutes, productSearchJitterMinutes);
    }

    public Duration userProfileTtl() {
        return minutesWithJitter(userProfileMinutes, userProfileJitterMinutes);
    }

    public Duration recommendationCandidatesTtl() {
        return minutesWithJitter(recommendationCandidatesMinutes, recommendationCandidatesJitterMinutes);
    }

    public Duration assistantRateLimitTtl() {
        return Duration.ofSeconds(assistantRateLimitSeconds);
    }

    public void setProductDetailMinutes(long productDetailMinutes) {
        this.productDetailMinutes = productDetailMinutes;
    }

    public void setProductDetailJitterMinutes(long productDetailJitterMinutes) {
        this.productDetailJitterMinutes = productDetailJitterMinutes;
    }

    public void setProductSearchMinutes(long productSearchMinutes) {
        this.productSearchMinutes = productSearchMinutes;
    }

    public void setProductSearchJitterMinutes(long productSearchJitterMinutes) {
        this.productSearchJitterMinutes = productSearchJitterMinutes;
    }

    public void setUserProfileMinutes(long userProfileMinutes) {
        this.userProfileMinutes = userProfileMinutes;
    }

    public void setUserProfileJitterMinutes(long userProfileJitterMinutes) {
        this.userProfileJitterMinutes = userProfileJitterMinutes;
    }

    public void setRecommendationCandidatesMinutes(long recommendationCandidatesMinutes) {
        this.recommendationCandidatesMinutes = recommendationCandidatesMinutes;
    }

    public void setRecommendationCandidatesJitterMinutes(long recommendationCandidatesJitterMinutes) {
        this.recommendationCandidatesJitterMinutes = recommendationCandidatesJitterMinutes;
    }

    public void setAssistantRateLimitSeconds(long assistantRateLimitSeconds) {
        this.assistantRateLimitSeconds = assistantRateLimitSeconds;
    }

    private Duration minutesWithJitter(long baseMinutes, long jitterMinutes) {
        if (jitterMinutes <= 0) {
            return Duration.ofMinutes(baseMinutes);
        }
        return Duration.ofMinutes(baseMinutes + ThreadLocalRandom.current().nextLong(jitterMinutes + 1));
    }
}
