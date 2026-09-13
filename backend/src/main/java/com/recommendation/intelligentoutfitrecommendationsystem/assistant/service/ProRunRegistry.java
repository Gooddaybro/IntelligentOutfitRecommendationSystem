package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Redis-backed authority for a single Pro run; only Java query results enter its ledger.
 * Atomic scripts keep authorization, fixed expiry and capacity consistent across instances.
 * Redis failures never fall back to an unverified local ledger.
 */
@Service
public class ProRunRegistry {
    private static final String PREFIX = "assistant:pro:run:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
            redis.call('HSET', KEYS[1], 'token', ARGV[1], 'user', ARGV[2],
                'thread', ARGV[3], 'request', ARGV[4], 'count', '0')
            redis.call('EXPIRE', KEYS[1], 600)
            return 1
            """, Long.class);
    private static final String AUTH = """
            if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then return -1 end
            """;
    private static final DefaultRedisScript<Long> AUTHORIZE = new DefaultRedisScript<>(AUTH + "return 1", Long.class);
    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>(AUTH
            + "redis.call('DEL', KEYS[1]); return 1", Long.class);
    private static final DefaultRedisScript<Long> REGISTER = new DefaultRedisScript<>(AUTH + """
            local added = 0
            local seen = {}
            for i = 2, #ARGV, 2 do
                if not seen[ARGV[i]] and redis.call('HEXISTS', KEYS[1], ARGV[i]) == 0 then
                    added = added + 1
                    seen[ARGV[i]] = true
                end
            end
            if tonumber(redis.call('HGET', KEYS[1], 'count')) + added > 200 then return -2 end
            for i = 2, #ARGV, 2 do redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1]) end
            redis.call('HINCRBY', KEYS[1], 'count', added)
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> DETAIL = new DefaultRedisScript<>(AUTH
            + "redis.call('HSET', KEYS[1], ARGV[2], '1'); return 1", Long.class);
    private static final DefaultRedisScript<List> SNAPSHOT = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'token') ~= ARGV[1] then return {} end
            return redis.call('HGETALL', KEYS[1])
            """, List.class);

    private final StringRedisTemplate redis;

    public ProRunRegistry(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Creates server-owned binding with a 256-bit secret and a nonrenewable ten-minute expiry. */
    public RunCredentials create(Long userId, String threadId, String requestId) {
        if (userId == null || userId <= 0 || threadId == null || threadId.isBlank()
                || requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("Run identity must be supplied by Java");
        }
        String runId = UUID.randomUUID().toString();
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        check(execute(CREATE, runId, digest(token), userId.toString(), threadId, requestId));
        return new RunCredentials(runId, token);
    }

    /** Rejects missing, expired, revoked or foreign-run credentials before catalog access. */
    public void authorize(String runId, String token) {
        check(execute(AUTHORIZE, validRunId(runId), validDigest(token)));
    }

    /**
     * Registers one atomic batch of actual query results, preserving price snapshots.
     * Duplicate pairs update facts without consuming capacity; overflow writes nothing.
     * A late query cannot recreate a revoked run or refresh its original expiry.
     */
    public void registerCandidates(String runId, String token, List<RecommendationCandidate> candidates) {
        List<Object> args = new ArrayList<>();
        args.add(validDigest(token));
        for (RecommendationCandidate candidate : candidates) {
            if (candidate.getSpuId() == null || candidate.getSpuId() <= 0
                    || candidate.getSkuId() == null || candidate.getSkuId() <= 0
                    || candidate.getSalePrice() == null || candidate.getSalePrice().signum() < 0) {
                throw new RegistryException(Failure.UNAVAILABLE);
            }
            args.add("p:" + candidate.getSpuId() + ":" + candidate.getSkuId());
            args.add(candidate.getSalePrice().toPlainString());
        }
        check(execute(REGISTER, validRunId(runId), args.toArray()));
    }

    /** Records SPU detail provenance without authorizing any of the SPU's unqueried SKUs. */
    public void registerDetail(String runId, String token, Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new RegistryException(Failure.UNAVAILABLE);
        }
        check(execute(DETAIL, validRunId(runId), validDigest(token), "d:" + spuId));
    }

    /** Returns an immutable, token-free snapshot for later Java final-reference validation. */
    public RunSnapshot snapshot(String runId, String token) {
        List<?> values = execute(SNAPSHOT, validRunId(runId), validDigest(token));
        if (values.isEmpty()) {
            throw new RegistryException(Failure.FORBIDDEN);
        }
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            for (int i = 0; i < values.size(); i += 2) {
                fields.put((String) values.get(i), (String) values.get(i + 1));
            }
            Map<String, CandidateFact> pairs = new LinkedHashMap<>();
            Set<Long> details = new LinkedHashSet<>();
            fields.forEach((field, value) -> {
                if (field.startsWith("p:")) {
                    String[] ids = field.substring(2).split(":");
                    pairs.put(field.substring(2), new CandidateFact(Long.valueOf(ids[0]), Long.valueOf(ids[1]), new BigDecimal(value)));
                } else if (field.startsWith("d:")) {
                    details.add(Long.valueOf(field.substring(2)));
                }
            });
            return new RunSnapshot(Long.valueOf(fields.get("user")), fields.get("thread"), fields.get("request"),
                    Map.copyOf(pairs), Set.copyOf(details));
        } catch (RuntimeException error) {
            throw new RegistryException(Failure.UNAVAILABLE);
        }
    }

    /** Removes credentials and ledger atomically; wrong credentials cannot close another run. */
    public void revoke(String runId, String token) {
        check(execute(REVOKE, validRunId(runId), validDigest(token)));
    }

    private <T> T execute(DefaultRedisScript<T> script, String runId, Object... args) {
        try {
            T result = redis.execute(script, List.of(PREFIX + runId), args);
            if (result == null) {
                throw new RegistryException(Failure.UNAVAILABLE);
            }
            return result;
        } catch (RuntimeException error) {
            throw new RegistryException(Failure.UNAVAILABLE);
        }
    }

    private static void check(Long result) {
        if (result == -1) { throw new RegistryException(Failure.FORBIDDEN); }
        if (result == -2) { throw new RegistryException(Failure.CAPACITY); }
        if (result != 1) { throw new RegistryException(Failure.UNAVAILABLE); }
    }

    private static String validRunId(String runId) {
        if (runId == null || !runId.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) {
            throw new RegistryException(Failure.FORBIDDEN);
        }
        return runId;
    }

    private static String validDigest(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new RegistryException(Failure.FORBIDDEN);
        }
        return digest(token);
    }

    private static String digest(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 required");
        }
    }

    /** Credentials are transport-only; generated toString must never disclose the secret. */
    public record RunCredentials(String runId, String token) {
        @Override
        public String toString() { return "RunCredentials[runId=" + runId + ", token=REDACTED]"; }
    }

    /** Java-returned SPU/SKU pair and its unscaled decimal CNY query-time price. */
    public record CandidateFact(Long spuId, Long skuId, BigDecimal salePrice) { }

    /** Binding and observed facts only; neither token nor caller-selected identity is exposed. */
    public record RunSnapshot(Long userId, String threadId, String requestId,
                              Map<String, CandidateFact> candidates, Set<Long> detailSpuIds) { }

    /** Distinguishes rejected credentials from unavailable authority and bounded ledger capacity. */
    public enum Failure { FORBIDDEN, UNAVAILABLE, CAPACITY }

    /** Safe failure category without Redis exception text, keys, or runtime credentials. */
    public static class RegistryException extends RuntimeException {
        private final Failure failure;
        public RegistryException(Failure failure) {
            super("Pro run " + failure.name().toLowerCase(java.util.Locale.ROOT));
            this.failure = failure;
        }
        public Failure failure() { return failure; }
    }
}
