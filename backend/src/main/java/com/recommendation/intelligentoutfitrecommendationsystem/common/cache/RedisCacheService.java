package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Wraps Redis JSON cache operations and keeps cache outages from blocking core commerce and AI flows.
 */
@Service
public class RedisCacheService {

    private static final DefaultRedisScript<Long> INCREMENT_WITH_TTL_SCRIPT = new DefaultRedisScript<>(
            """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationMetrics metrics;

    public RedisCacheService(StringRedisTemplate redisTemplate, ApplicationMetrics metrics) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public <T> Optional<T> getValue(String key, Class<T> valueType) {
        long startedAt = System.nanoTime();
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                record("get", "miss", startedAt);
                return Optional.empty();
            }
            T value = objectMapper.readValue(json, valueType);
            record("get", "hit", startedAt);
            return Optional.of(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            record("get", "error", startedAt);
            return Optional.empty();
        }
    }

    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        long startedAt = System.nanoTime();
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                record("get", "miss", startedAt);
                return Optional.empty();
            }
            var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            List<T> value = objectMapper.readValue(json, listType);
            record("get", "hit", startedAt);
            return Optional.of(value);
        } catch (JsonProcessingException | RuntimeException exception) {
            record("get", "error", startedAt);
            return Optional.empty();
        }
    }

    public void setValue(String key, Object value, Duration ttl) {
        long startedAt = System.nanoTime();
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, json);
                record("set", "success", startedAt);
                return;
            }
            redisTemplate.opsForValue().set(key, json, ttl);
            record("set", "success", startedAt);
        } catch (JsonProcessingException | RuntimeException exception) {
            record("set", "error", startedAt);
            // Redis is an acceleration layer; MySQL remains the source of truth.
        }
    }

    public void delete(String key) {
        long startedAt = System.nanoTime();
        try {
            redisTemplate.delete(key);
            record("delete", "success", startedAt);
        } catch (RuntimeException exception) {
            record("delete", "error", startedAt);
            // Redis is an acceleration layer; MySQL remains the source of truth.
        }
    }

    public Optional<Long> incrementWithTtl(String key, Duration ttl) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("redis key must not be blank");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("redis counter ttl must be positive");
        }
        long startedAt = System.nanoTime();
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_WITH_TTL_SCRIPT,
                    List.of(key),
                    Long.toString(ttl.toMillis())
            );
            record("increment", count == null ? "error" : "success", startedAt);
            return Optional.ofNullable(count);
        } catch (RuntimeException exception) {
            record("increment", "error", startedAt);
            return Optional.empty();
        }
    }

    private void record(String operation, String outcome, long startedAt) {
        metrics.recordRedisCommand(operation, outcome, Duration.ofNanos(System.nanoTime() - startedAt));
    }
}
