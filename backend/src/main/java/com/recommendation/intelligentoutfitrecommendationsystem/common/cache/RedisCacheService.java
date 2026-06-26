package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Redis 缓存访问封装。
 *
 * 业务层通过该服务读写 JSON 缓存，避免直接依赖 RedisTemplate；Redis 异常时采用
 * 失败放行策略，让 MySQL 事实源继续服务核心业务。
 */
@Service
public class RedisCacheService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public RedisCacheService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public <T> Optional<T> getValue(String key, Class<T> valueType) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, valueType));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Redis cache value cannot be deserialized, key={}", key, exception);
            delete(key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache read failed, key={}", key, exception);
            return Optional.empty();
        }
    }

    public void setValue(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Redis cache value cannot be serialized, key={}", key, exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache write failed, key={}", key, exception);
        }
    }

    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache delete failed, key={}", key, exception);
        }
    }
}
