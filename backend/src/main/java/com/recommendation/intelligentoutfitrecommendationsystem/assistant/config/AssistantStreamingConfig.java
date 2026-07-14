package com.recommendation.intelligentoutfitrecommendationsystem.assistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * AI 导购流式接口的后台执行器配置。
 *
 * SSE 请求生命周期长于普通 HTTP 请求，使用有界线程池避免并发流式请求无限创建工作线程。
 */
@Configuration
public class AssistantStreamingConfig {

    @Bean("assistantStreamingExecutor")
    public Executor assistantStreamingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("assistant-stream-");
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(task -> {
            Map<String, String> callerContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previousContext = MDC.getCopyOfContextMap();
                try {
                    if (callerContext == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(callerContext);
                    }
                    task.run();
                } finally {
                    if (previousContext == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previousContext);
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
