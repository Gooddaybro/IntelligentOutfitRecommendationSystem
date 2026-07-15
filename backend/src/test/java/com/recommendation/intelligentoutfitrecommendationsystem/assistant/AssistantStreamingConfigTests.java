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
        MDC.put("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        CompletableFuture<String> first = new CompletableFuture<>();

        executor.execute(() -> first.complete(MDC.get("requestId") + ":" + MDC.get("traceparent")));

        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo(
                "req-stream-context:00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MDC.clear();
        CompletableFuture<String> second = new CompletableFuture<>();
        executor.execute(() -> second.complete(MDC.get("requestId")));
        assertThat(second.get(1, TimeUnit.SECONDS)).isNull();
    }
}
