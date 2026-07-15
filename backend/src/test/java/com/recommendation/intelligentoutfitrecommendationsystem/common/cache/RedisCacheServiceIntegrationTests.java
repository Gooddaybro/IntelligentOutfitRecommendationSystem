package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisCacheServiceIntegrationTests {

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")
    ).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RedisCacheService service;

    @BeforeAll
    static void setUpRedisClient() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        service = new RedisCacheService(redisTemplate, new ApplicationMetrics(new SimpleMeterRegistry()));
    }

    @AfterAll
    static void closeRedisClient() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void atomicCounterReturnsIncrementedValuesAndKeepsTtl() {
        String key = "test:rate-limit:sequential";
        redisTemplate.delete(key);

        assertThat(service.incrementWithTtl(key, Duration.ofSeconds(30))).contains(1L);
        assertThat(service.incrementWithTtl(key, Duration.ofSeconds(30))).contains(2L);

        Long ttlMillis = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        assertThat(ttlMillis).isPositive().isLessThanOrEqualTo(30_000L);
    }

    @Test
    void concurrentAtomicCountersProduceUniqueCountsAndOneExpiringKey() throws Exception {
        String key = "test:rate-limit:concurrent";
        redisTemplate.delete(key);
        int requestCount = 20;
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<java.util.concurrent.Callable<Long>> tasks = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(ignored -> (java.util.concurrent.Callable<Long>) () ->
                            service.incrementWithTtl(key, Duration.ofSeconds(30)).orElseThrow())
                    .toList();

            var results = executor.invokeAll(tasks).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();

            assertThat(new HashSet<>(results)).hasSize(requestCount);
            assertThat(results).contains(1L, (long) requestCount);
            assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isPositive();
        }
    }
}
