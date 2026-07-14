package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

    public RedisCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public <T> Optional<T> getValue(String key, Class<T> valueType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, valueType));
        } catch (JsonProcessingException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return Optional.of(objectMapper.readValue(json, listType));
        } catch (JsonProcessingException | RuntimeException exception) {
            return Optional.empty();
        }
    }

    public void setValue(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            if (ttl == null || ttl.isZero() || ttl.isNegative()) {
                redisTemplate.opsForValue().set(key, json);
                return;
            }
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (JsonProcessingException | RuntimeException exception) {
            // Redis is an acceleration layer; MySQL remains the source of truth.
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (RuntimeException exception) {
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
        try {
            Long count = redisTemplate.execute(
                    INCREMENT_WITH_TTL_SCRIPT,
                    List.of(key),
                    ttl.toMillis()
            );
            return Optional.ofNullable(count);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
