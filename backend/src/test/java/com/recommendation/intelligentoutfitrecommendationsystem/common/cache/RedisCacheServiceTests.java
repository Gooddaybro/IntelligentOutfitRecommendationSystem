package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisCacheServiceTests {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisCacheService service;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RedisCacheService(redisTemplate, new ApplicationMetrics(meterRegistry));
    }

    @Test
    void incrementWithTtlExecutesOneAtomicScriptWithoutSeparateExpire() {
        when(redisTemplate.execute(
                any(RedisScript.class),
                eq(List.of("assistant:rate-limit:user:10:1")),
                eq("60000")
        )).thenReturn(1L);

        var count = service.incrementWithTtl(
                "assistant:rate-limit:user:10:1",
                Duration.ofSeconds(60)
        );

        assertThat(count).contains(1L);
        verify(redisTemplate, never()).expire(any(String.class), any(Duration.class));
        assertThat(meterRegistry.get("app.redis.commands")
                .tags("operation", "increment", "outcome", "success").counter().count()).isEqualTo(1);
    }

    @Test
    void incrementWithTtlRejectsNonPositiveTtlBeforeCallingRedis() {
        assertThatThrownBy(() -> service.incrementWithTtl("rate-limit:key", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");

        verify(redisTemplate, never()).execute(any(RedisScript.class), any(List.class), any());
    }
}
