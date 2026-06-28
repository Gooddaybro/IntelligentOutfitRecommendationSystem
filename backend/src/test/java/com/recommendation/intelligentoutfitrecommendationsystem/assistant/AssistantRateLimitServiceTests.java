package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantRateLimitService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.CacheTtlProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.common.cache.RedisCacheService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantRateLimitServiceTests {

    @Mock
    private RedisCacheService redisCacheService;

    private AssistantRateLimitService service;

    @BeforeEach
    void setUp() {
        CacheTtlProperties cacheTtlProperties = new CacheTtlProperties();
        cacheTtlProperties.setAssistantRateLimitSeconds(60);
        service = new AssistantRateLimitService(redisCacheService, cacheTtlProperties, 2);
    }

    @Test
    void allowsRequestWhenUserCountIsWithinLimit() {
        when(redisCacheService.incrementWithTtl(anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(Optional.of(2L));

        assertThatCode(() -> service.assertAllowed(1001L))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsRequestWhenUserCountExceedsLimit() {
        when(redisCacheService.incrementWithTtl(anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(Optional.of(3L));

        assertThatThrownBy(() -> service.assertAllowed(1001L))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("AI requests are too frequent. Please try again later.");
    }

    @Test
    void allowsRequestWhenRedisCounterFailsOpen() {
        when(redisCacheService.incrementWithTtl(anyString(), eq(Duration.ofSeconds(60))))
                .thenReturn(Optional.empty());

        assertThatCode(() -> service.assertAllowed(1001L))
                .doesNotThrowAnyException();
    }
}
