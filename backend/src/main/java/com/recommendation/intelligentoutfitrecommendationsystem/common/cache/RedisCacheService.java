package com.recommendation.intelligentoutfitrecommendationsystem.common.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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

    /**
     * 读取单个 JSON 缓存对象。
     *
     * Redis 读取失败或 JSON 反序列化失败时返回 empty，让业务继续回源 MySQL。
     */
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

    /**
     * 读取 JSON 数组缓存。
     *
     * 推荐候选返回的是列表，必须显式传入元素类型，否则 Jackson 无法可靠还原列表元素。
     */
    public <T> Optional<List<T>> getList(String key, Class<T> elementType) {
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            var listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return Optional.of(objectMapper.readValue(json, listType));
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Redis cache list cannot be deserialized, key={}", key, exception);
            delete(key);
            return Optional.empty();
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache list read failed, key={}", key, exception);
            return Optional.empty();
        }
    }

    /**
     * 写入 JSON 缓存并设置 TTL。
     *
     * 写缓存失败不影响主流程，因为 MySQL 才是商品和用户数据的事实源。
     */
    public void setValue(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Redis cache value cannot be serialized, key={}", key, exception);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache write failed, key={}", key, exception);
        }
    }

    /**
     * 删除指定缓存 key。
     *
     * 数据更新后删除缓存，让下一次读取重新从 MySQL 加载新值。
     */
    public void delete(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache delete failed, key={}", key, exception);
        }
    }

    /**
     * 对计数 key 执行自增，并在首次创建时设置 TTL。
     *
     * AI 限流使用该方法维护分钟级计数；Redis 异常时返回 empty，由调用方决定是否放行。
     */
    public Optional<Long> incrementWithTtl(String key, Duration ttl) {
        try {
            Long count = stringRedisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                stringRedisTemplate.expire(key, ttl);
            }
            return Optional.ofNullable(count);
        } catch (RuntimeException exception) {
            LOGGER.warn("Redis cache increment failed, key={}", key, exception);
            return Optional.empty();
        }
    }
}
