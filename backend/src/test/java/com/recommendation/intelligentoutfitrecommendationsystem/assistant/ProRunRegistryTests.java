package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ProRunRegistryTests {
    private static LettuceConnectionFactory connection;
    private static StringRedisTemplate redis;
    private static ProRunRegistry registry;

    @BeforeAll
    static void connect() {
        String port = System.getProperty("pro.test.redis.port");
        assumeTrue(port != null, "Set -Dpro.test.redis.port to run actual Redis Lua tests");
        connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(port));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
        registry = new ProRunRegistry(redis);
    }

    @AfterAll
    static void close() {
        if (connection != null) { connection.destroy(); }
    }

    @Test
    void bindingSecretTtlIsolationAndRevocation() {
        var first = registry.create(7L, "thread-a", "request-a");
        var second = registry.create(8L, "thread-b", "request-b");
        try {
            assertThat(first.token()).hasSizeGreaterThanOrEqualTo(43);
            assertThat(first.toString()).doesNotContain(first.token());
            var snapshot = registry.snapshot(first.runId(), first.token());
            assertThat(snapshot.userId()).isEqualTo(7L);
            assertThat(snapshot.threadId()).isEqualTo("thread-a");
            assertThat(snapshot.requestId()).isEqualTo("request-a");
            assertThrows(ProRunRegistry.RegistryException.class, () -> registry.authorize(first.runId(), second.token()));
            Long ttl = redis.getExpire("assistant:pro:run:" + first.runId(), TimeUnit.MILLISECONDS);
            assertThat(ttl).isBetween(590_000L, 600_000L);
            registry.registerCandidates(first.runId(), first.token(), List.of(ProToolQueryServiceTests.candidate(1, "299.90")));
            registry.registerDetail(first.runId(), first.token(), 99L);
            snapshot = registry.snapshot(first.runId(), first.token());
            assertThat(snapshot.candidates()).hasSize(1);
            assertThat(snapshot.candidates().get("10:1").salePrice()).isEqualByComparingTo("299.90");
            assertThat(snapshot.detailSpuIds()).containsExactly(99L);
            assertThat(registry.snapshot(second.runId(), second.token()).candidates()).isEmpty();
            assertThat(redis.getExpire("assistant:pro:run:" + first.runId(), TimeUnit.MILLISECONDS)).isLessThanOrEqualTo(ttl);
            registry.revoke(first.runId(), first.token());
            assertThrows(ProRunRegistry.RegistryException.class, () -> registry.registerCandidates(first.runId(), first.token(),
                    List.of(ProToolQueryServiceTests.candidate(2, "1"))));
            assertThat(redis.hasKey("assistant:pro:run:" + first.runId())).isFalse();
        } finally {
            redis.delete(List.of("assistant:pro:run:" + first.runId(), "assistant:pro:run:" + second.runId()));
        }
    }

    @Test
    void expiredRunRejectsReadsAndWrites() {
        var run = registry.create(7L, "thread", "request");
        redis.expire("assistant:pro:run:" + run.runId(), Duration.ZERO);
        assertThrows(ProRunRegistry.RegistryException.class, () -> registry.authorize(run.runId(), run.token()));
        assertThrows(ProRunRegistry.RegistryException.class, () -> registry.registerDetail(run.runId(), run.token(), 10L));
        assertThat(redis.hasKey("assistant:pro:run:" + run.runId())).isFalse();
    }

    @Test
    void capacityIsAtomicAcrossInstancesAndDuplicatePairsDoNotConsumeSlots() throws Exception {
        var run = registry.create(7L, "thread", "request");
        var secondInstance = new ProRunRegistry(redis);
        try (var executor = Executors.newFixedThreadPool(4)) {
            registry.registerCandidates(run.runId(), run.token(),
                    LongStream.rangeClosed(1, 190).mapToObj(id -> ProToolQueryServiceTests.candidate(id, "10")).toList());
            List<Callable<Boolean>> tasks = LongStream.rangeClosed(191, 210).mapToObj(id -> (Callable<Boolean>) () -> {
                try {
                    secondInstance.registerCandidates(run.runId(), run.token(), List.of(ProToolQueryServiceTests.candidate(id, "10")));
                    return true;
                } catch (ProRunRegistry.RegistryException error) {
                    assertThat(error.failure()).isEqualTo(ProRunRegistry.Failure.CAPACITY);
                    return false;
                }
            }).toList();
            int successes = 0;
            for (var future : executor.invokeAll(tasks)) { if (future.get()) { successes++; } }
            assertThat(successes).isEqualTo(10);
            registry.registerCandidates(run.runId(), run.token(), List.of(ProToolQueryServiceTests.candidate(1, "12")));
            assertThat(registry.snapshot(run.runId(), run.token()).candidates()).hasSize(200);
            assertThrows(ProRunRegistry.RegistryException.class, () -> registry.registerCandidates(run.runId(), run.token(),
                    List.of(ProToolQueryServiceTests.candidate(1, "15"), ProToolQueryServiceTests.candidate(999, "15"))));
            assertThat(registry.snapshot(run.runId(), run.token()).candidates().get("10:1").salePrice()).isEqualByComparingTo("12");
        } finally { redis.delete("assistant:pro:run:" + run.runId()); }
    }
}
