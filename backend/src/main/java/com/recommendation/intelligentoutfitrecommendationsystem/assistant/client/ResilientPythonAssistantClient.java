package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.Duration;

/**
 * 为 Python HTTP Adapter 增加同步与 SSE 共用的可恢复熔断策略。
 *
 * 该模块不生成业务降级文案，只决定远程调用是否被允许，并将流式调用终态记录到 CircuitBreaker。
 */
@Primary
@Component
@ConditionalOnProperty(name = "app.ai.circuit-breaker.enabled", havingValue = "true", matchIfMissing = true)
public class ResilientPythonAssistantClient implements PythonAssistantClient, PythonAssistantStreamClient {

    private final RestPythonAssistantClient delegate;
    private final CircuitBreaker circuitBreaker;
    private final ApplicationMetrics metrics;

    public ResilientPythonAssistantClient(
            RestPythonAssistantClient delegate,
            CircuitBreaker pythonAssistantCircuitBreaker,
            ApplicationMetrics metrics
    ) {
        this.delegate = delegate;
        this.circuitBreaker = pythonAssistantCircuitBreaker;
        this.metrics = metrics;
    }

    @Override
    public PythonChatResponse chat(PythonChatRequest request) {
        long startedAt = System.nanoTime();
        try {
            PythonChatResponse response = circuitBreaker.executeSupplier(() -> delegate.chat(request));
            record("sync", "success", startedAt);
            return response;
        } catch (CallNotPermittedException exception) {
            record("sync", "circuit_open", startedAt);
            throw exception;
        } catch (RuntimeException exception) {
            record("sync", "error", startedAt);
            throw exception;
        }
    }

    @Override
    public void streamChat(PythonChatRequest request, PythonAssistantStreamHandler handler) {
        if (!circuitBreaker.tryAcquirePermission()) {
            metrics.recordAiRequest("stream", "circuit_open", Duration.ZERO);
            handler.onError("python_circuit_open", "python assistant circuit is open");
            return;
        }
        long startedAt = System.nanoTime();
        AtomicBoolean terminalRecorded = new AtomicBoolean();
        PythonAssistantStreamHandler recordingHandler = new RecordingHandler(
                handler,
                terminalRecorded,
                startedAt
        );
        try {
            delegate.streamChat(request, recordingHandler);
            if (terminalRecorded.compareAndSet(false, true)) {
                ExternalServiceException exception =
                        new ExternalServiceException("python assistant stream ended without terminal event");
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
                record("stream", "error", startedAt);
                handler.onError("python_stream_incomplete", "python assistant stream ended unexpectedly");
            }
        } catch (RuntimeException exception) {
            if (terminalRecorded.compareAndSet(false, true)) {
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
                record("stream", "error", startedAt);
            }
            throw exception;
        }
    }

    private long elapsedNanos(long startedAt) {
        return System.nanoTime() - startedAt;
    }

    private void record(String mode, String outcome, long startedAt) {
        metrics.recordAiRequest(mode, outcome, Duration.ofNanos(elapsedNanos(startedAt)));
    }

    private final class RecordingHandler implements PythonAssistantStreamHandler {
        private final PythonAssistantStreamHandler delegate;
        private final AtomicBoolean terminalRecorded;
        private final long startedAt;

        private RecordingHandler(
                PythonAssistantStreamHandler delegate,
                AtomicBoolean terminalRecorded,
                long startedAt
        ) {
            this.delegate = delegate;
            this.terminalRecorded = terminalRecorded;
            this.startedAt = startedAt;
        }

        @Override
        public void onToken(String content) {
            delegate.onToken(content);
        }

        @Override
        public void onDone(PythonChatResponse response) {
            if (terminalRecorded.compareAndSet(false, true)) {
                circuitBreaker.onSuccess(elapsedNanos(startedAt), TimeUnit.NANOSECONDS);
                record("stream", "success", startedAt);
            }
            delegate.onDone(response);
        }

        @Override
        public void onError(String code, String message) {
            if (terminalRecorded.compareAndSet(false, true)) {
                ExternalServiceException exception = new ExternalServiceException(message);
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
                record("stream", "error", startedAt);
            }
            delegate.onError(code, message);
        }
    }
}
