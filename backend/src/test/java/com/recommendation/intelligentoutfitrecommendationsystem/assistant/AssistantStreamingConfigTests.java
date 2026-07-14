package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.config.AssistantStreamingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantStreamingConfigTests {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void propagatesAndClearsMdcForStreamingTasks() throws Exception {
        Executor configured = new AssistantStreamingConfig().assistantStreamingExecutor();
        executor = (ThreadPoolTaskExecutor) configured;
        MDC.put("requestId", "req-stream-context");
        CompletableFuture<String> first = new CompletableFuture<>();

        executor.execute(() -> first.complete(MDC.get("requestId")));

        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("req-stream-context");
        MDC.clear();
        CompletableFuture<String> second = new CompletableFuture<>();
        executor.execute(() -> second.complete(MDC.get("requestId")));
        assertThat(second.get(1, TimeUnit.SECONDS)).isNull();
    }
}
