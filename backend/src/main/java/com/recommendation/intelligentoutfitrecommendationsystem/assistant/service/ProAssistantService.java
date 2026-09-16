package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.ProPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProDoneEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProProgressEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationRecordCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Independent Pro preparation, final-fact validation and delivery lifecycle.
 *
 * <p>Lite has a separate service and event handler.  This service keeps Python's generated
 * answer buffered until Java has re-read the live product facts and persisted the final turn.</p>
 */
@Service
public class ProAssistantService {
    private final ConversationApplicationService conversations;
    private final AssistantRateLimitService limiter;
    private final ProContextService context;
    private final ProRunRegistry registry;
    private final ProPythonAssistantClient client;
    private final ProRecommendationValidator validator;
    private final RecommendationAttributionService recommendationAttribution;
    private final Executor streamExecutor;
    private final long streamTimeoutMs;
    private final boolean enabled;

    /**
     * Compatibility constructor for the Task 3 validation-only tests and callers.
     *
     * <p>The Spring application uses the full constructor below; this overload deliberately
     * leaves streaming-only collaborators absent because validation-only callers never stream.</p>
     */
    public ProAssistantService(ConversationApplicationService conversations, AssistantRateLimitService limiter,
                               ProContextService context, ProRunRegistry registry, ProPythonAssistantClient client,
                               ProRecommendationValidator validator,
                               @Value("${app.ai.pro-enabled:false}") boolean enabled) {
        this(conversations, limiter, context, registry, client, validator, null, null, 120000L, enabled);
    }

    /**
     * Creates the Pro boundary used by the application.
     *
     * <p>The executor allows a disconnected HTTP client to cancel the Python read without
     * blocking the request thread.  A bounded emitter timeout covers Python's shorter execution
     * deadline plus Java persistence and final-event delivery.</p>
     */
    @Autowired
    public ProAssistantService(ConversationApplicationService conversations, AssistantRateLimitService limiter,
                               ProContextService context, ProRunRegistry registry, ProPythonAssistantClient client,
                               ProRecommendationValidator validator,
                               RecommendationAttributionService recommendationAttribution,
                               @Qualifier("assistantStreamingExecutor") Executor streamExecutor,
                               @Value("${app.ai.stream-timeout-ms:120000}") long streamTimeoutMs,
                               @Value("${app.ai.pro-enabled:false}") boolean enabled) {
        this.conversations = conversations;
        this.limiter = limiter;
        this.context = context;
        this.registry = registry;
        this.client = client;
        this.validator = validator;
        this.recommendationAttribution = recommendationAttribution;
        this.streamExecutor = streamExecutor;
        this.streamTimeoutMs = streamTimeoutMs;
        this.enabled = enabled;
    }

    /**
     * Saves an authorized user turn, exchanges v2 data, then revokes its tool credentials.
     * Returns safe public-shaped data; caller delivery still requires the final persistence stage.
     */
    public JsonNode exchangeForValidation(Long userId, ProChatRequest request) {
        if (!enabled) {
            throw new ProUnavailableException("pro_disabled");
        }
        if (userId == null || userId <= 0 || !"pro".equals(request.agentMode())) {
            throw new BadRequestException("Invalid Pro request");
        }
        limiter.assertAllowed(userId);
        String threadId = request.threadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = conversations.createConversation(userId, "Pro 导购").threadId();
        }
        conversations.assertOwned(userId, threadId);
        String requestId = "req-" + UUID.randomUUID();
        conversations.appendMessage(userId, threadId, "user", request.message(), requestId);
        ProRunRegistry.RunCredentials run = registry.create(userId, threadId, requestId);
        try {
            JsonNode done = client.chat(context.build(userId, threadId, request, requestId, run));
            return validator.validate(userId, threadId, requestId, run, request, done);
        } finally {
            registry.revoke(run.runId(), run.token());
        }
    }

    /**
     * Executes the synchronous public Pro path and persists the validated result before returning it.
     *
     * <p>The validation-only method above remains available for the Task 3 internal boundary; this
     * method is the public v2 contract and therefore never exposes its unpersisted intermediate JSON.</p>
     *
     * @param userId authenticated user identity
     * @param request public Pro request
     * @return final Java-owned result equal in shape to the stream terminal event
     */
    @Transactional
    public ProDoneEvent chat(Long userId, ProChatRequest request) {
        validateRequest(userId, request);
        limiter.assertAllowed(userId);
        String threadId = resolveThreadId(userId, request);
        String requestId = requestId();
        conversations.appendMessage(userId, threadId, "user", request.message(), requestId);
        ProRunRegistry.RunCredentials run = registry.create(userId, threadId, requestId);
        try {
            JsonNode upstream = client.chat(context.build(userId, threadId, request, requestId, run));
            JsonNode validated = validator.validate(userId, threadId, requestId, run, request, upstream);
            String recommendationId = persistValidated(userId, threadId, requestId, "sync", validated);
            return toDone(validated, threadId, run.runId(), recommendationId);
        } finally {
            lifecycleRevoke(run);
        }
    }

    private void lifecycleRevoke(ProRunRegistry.RunCredentials run) {
        try {
            registry.revoke(run.runId(), run.token());
        } catch (RuntimeException ignored) {
            // The run TTL is the cleanup fallback when the authority is already unavailable.
        }
    }

    /**
     * Opens the public Pro SSE stream and keeps its terminal transition single-shot.
     *
     * <p>Progress can be sent as soon as it is received.  Python answer tokens are retained in
     * memory and never reach the browser until {@link ProRecommendationValidator} has approved
     * the result and the answer plus recommendation snapshot have been saved.</p>
     *
     * @param userId authenticated user identity
     * @param request public Pro request
     * @return asynchronous SSE emitter with meta/progress/token/done or one error
     */
    public SseEmitter streamChat(Long userId, ProChatRequest request) {
        validateRequest(userId, request);
        limiter.assertAllowed(userId);
        String threadId = resolveThreadId(userId, request);
        String requestId = requestId();
        conversations.appendMessage(userId, threadId, "user", request.message(), requestId);
        ProRunRegistry.RunCredentials run = registry.create(userId, threadId, requestId);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        RunLifecycle lifecycle = new RunLifecycle(run, emitter);
        emitter.onCompletion(lifecycle::cancel);
        emitter.onTimeout(() -> {
            lifecycle.cancel();
            emitter.complete();
        });
        emitter.onError(error -> lifecycle.cancel());

        if (!send(emitter, lifecycle, "meta", meta(requestId, threadId, run.runId()))) {
            lifecycle.cancel();
            return emitter;
        }

        ProPythonChatRequest pythonRequest;
        try {
            pythonRequest = context.build(userId, threadId, request, requestId, run);
        } catch (RuntimeException error) {
            finishError(lifecycle, "pro_context_unavailable");
            return emitter;
        }

        ProStreamHandler handler = new ProStreamHandler(
                userId, threadId, requestId, request, run, emitter, lifecycle);
        FutureTask<Void> task = new FutureTask<>(() -> {
            try {
                client.streamChat(pythonRequest, handler);
            } catch (RuntimeException error) {
                handler.onError("pro_python_unavailable", "Pro Python service is unavailable");
            } finally {
                lifecycle.revoke();
            }
            return null;
        });
        lifecycle.setFuture(task);
        try {
            if (streamExecutor == null) {
                throw new RejectedExecutionException("Pro stream executor is not configured");
            }
            streamExecutor.execute(task);
        } catch (RejectedExecutionException error) {
            finishError(lifecycle, "pro_stream_busy");
        }
        return emitter;
    }

    private void validateRequest(Long userId, ProChatRequest request) {
        if (!enabled) {
            throw new ProUnavailableException("pro_disabled");
        }
        if (userId == null || userId <= 0 || request == null || !"pro".equals(request.agentMode())) {
            throw new BadRequestException("Invalid Pro request");
        }
    }

    private String resolveThreadId(Long userId, ProChatRequest request) {
        String threadId = request.threadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = conversations.createConversation(userId, "Pro 导购").threadId();
        }
        conversations.assertOwned(userId, threadId);
        return threadId;
    }

    private String requestId() {
        String requestId = MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? "req-" + UUID.randomUUID() : requestId;
    }

    private java.util.Map<String, Object> meta(String requestId, String threadId, String runId) {
        return java.util.Map.of(
                "request_id", requestId,
                "thread_id", threadId,
                "run_id", runId,
                "agent_mode", "pro"
        );
    }

    private boolean send(SseEmitter emitter, RunLifecycle lifecycle, String name, Object data) {
        if (!lifecycle.active.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
            return true;
        } catch (java.io.IOException error) {
            lifecycle.cancel();
            return false;
        } catch (RuntimeException error) {
            lifecycle.cancel();
            return false;
        }
    }

    private void finishError(RunLifecycle lifecycle, String code) {
        if (!lifecycle.claimTerminal() || !lifecycle.active.get()) {
            lifecycle.revoke();
            return;
        }
        finishClaimedError(lifecycle, code);
    }

    private void finishClaimedError(RunLifecycle lifecycle, String code) {
        if (!lifecycle.active.get()) {
            lifecycle.revoke();
            return;
        }
        try {
            send(lifecycle.emitter, lifecycle, "error", java.util.Map.of(
                    "code", safeErrorCode(code),
                    "message", safeErrorMessage(code),
                    "run_id", lifecycle.run.runId()
            ));
        } finally {
            lifecycle.active.set(false);
            lifecycle.emitter.complete();
            lifecycle.revoke();
        }
    }

    private String safeErrorCode(String code) {
        return Set.of("pro_context_unavailable", "pro_python_unavailable", "pro_stream_busy",
                "pro_python_error", "pro_validation_failed", "pro_persistence_failed",
                "pro_stream_incomplete", "pro_stream_interrupted").contains(code)
                ? code : "pro_stream_error";
    }

    private String safeErrorMessage(String code) {
        return switch (safeErrorCode(code)) {
            case "pro_context_unavailable" -> "无法准备 Pro 导购上下文。";
            case "pro_stream_busy" -> "Pro 导购当前繁忙，请稍后重试。";
            case "pro_validation_failed" -> "Pro 导购结果未通过商品事实校验。";
            case "pro_persistence_failed" -> "Pro 导购结果保存失败，请稍后重试。";
            case "pro_stream_interrupted" -> "Pro 导购请求已取消。";
            default -> "Pro 导购暂时不可用，请稍后重试。";
        };
    }

    /** Saves only the already validated answer and the final selected references. */
    @Transactional
    protected String persistValidated(Long userId, String threadId, String requestId, String mode,
                                      JsonNode result) {
        String answer = result.path("answer").asText("");
        if (answer.isBlank() || recommendationAttribution == null) {
            throw new ExternalServiceException("Pro result persistence is unavailable");
        }
        List<RecommendationRecordCommand.Item> selected = selectedItems(result);
        List<RecommendationRecordCommand.Item> candidates = List.copyOf(selected);
        String recommendationId = recommendationAttribution.record(new RecommendationRecordCommand(
                userId, requestId, threadId, mode, candidates, selected));
        conversations.appendMessage(userId, threadId, "assistant", answer, requestId);
        return recommendationId;
    }

    private List<RecommendationRecordCommand.Item> selectedItems(JsonNode result) {
        List<RecommendationRecordCommand.Item> selected = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode item : result.path("recommended_items")) {
            if (!item.path("spu_id").canConvertToLong() || !item.path("sku_id").canConvertToLong()) {
                continue;
            }
            Long spuId = item.path("spu_id").longValue();
            Long skuId = item.path("sku_id").longValue();
            if (spuId <= 0 || skuId <= 0 || !seen.add(spuId + ":" + skuId)) {
                continue;
            }
            selected.add(new RecommendationRecordCommand.Item(spuId, skuId, null));
        }
        return List.copyOf(selected);
    }

    private ProDoneEvent toDone(JsonNode result, String threadId, String runId, String recommendationId) {
        List<ProDoneEvent.RecommendedItem> items = new ArrayList<>();
        Set<Long> spus = new LinkedHashSet<>();
        for (JsonNode item : result.path("recommended_items")) {
            if (!item.path("spu_id").canConvertToLong() || !item.path("sku_id").canConvertToLong()) {
                continue;
            }
            Long spuId = item.path("spu_id").longValue();
            Long skuId = item.path("sku_id").longValue();
            if (spuId <= 0 || skuId <= 0) {
                continue;
            }
            spus.add(spuId);
            items.add(new ProDoneEvent.RecommendedItem(
                    spuId, skuId, text(item, "name"), text(item, "sale_price"),
                    text(item, "main_image_url"), text(item, "color"), text(item, "size"),
                    item.path("available_stock").isIntegralNumber() ? item.path("available_stock").intValue() : null,
                    text(item, "reason"), text(item, "size_advice"), text(item, "basis")
            ));
        }
        List<Object> requirements = new ArrayList<>();
        result.path("requirements").forEach(requirement -> requirements.add(requirement.deepCopy()));
        return new ProDoneEvent(threadId, runId, "pro", text(result, "answer"), List.copyOf(spus),
                items, text(result, "recommendation_status"), requirements, recommendationId);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private final class ProStreamHandler implements ProPythonAssistantClient.ProStreamHandler {
        private final Long userId;
        private final String threadId;
        private final String requestId;
        private final ProChatRequest request;
        private final ProRunRegistry.RunCredentials run;
        private final SseEmitter emitter;
        private final RunLifecycle lifecycle;
        private final StringBuilder pythonTokens = new StringBuilder();

        private ProStreamHandler(Long userId, String threadId, String requestId, ProChatRequest request,
                                  ProRunRegistry.RunCredentials run, SseEmitter emitter, RunLifecycle lifecycle) {
            this.userId = userId;
            this.threadId = threadId;
            this.requestId = requestId;
            this.request = request;
            this.run = run;
            this.emitter = emitter;
            this.lifecycle = lifecycle;
        }

        @Override
        public void onProgress(ProProgressEvent event) {
            if (event != null && run.runId().equals(event.runId())) {
                send(emitter, lifecycle, "progress", event);
            }
        }

        @Override
        public void onToken(String content) {
            if (!lifecycle.active.get() || content == null || content.isEmpty()) {
                return;
            }
            if (pythonTokens.length() + content.length() > 8000) {
                finishError(lifecycle, "pro_validation_failed");
                return;
            }
            pythonTokens.append(content);
        }

        @Override
        public void onDone(JsonNode result) {
            if (!lifecycle.claimTerminal()) {
                return;
            }
            if (!lifecycle.active.get()) {
                lifecycle.revoke();
                return;
            }
            JsonNode validated;
            try {
                validated = validator.validate(userId, threadId, requestId, run, request, result);
                if (validated == null || !validated.path("answer").isTextual()
                        || validated.path("answer").asText().isBlank()) {
                    throw new ExternalServiceException("Pro result validation failed");
                }
            } catch (RuntimeException error) {
                finishClaimedError(lifecycle, "pro_validation_failed");
                lifecycle.revoke();
                return;
            }
            if (!lifecycle.active.get()) {
                lifecycle.revoke();
                return;
            }
            String recommendationId;
            try {
                recommendationId = persistValidated(userId, threadId, requestId, "stream", validated);
            } catch (RuntimeException error) {
                finishClaimedError(lifecycle, "pro_persistence_failed");
                lifecycle.revoke();
                return;
            }
            try {
                ProDoneEvent done = toDone(validated, threadId, run.runId(), recommendationId);
                if (!lifecycle.active.get()) {
                    return;
                }
                send(emitter, lifecycle, "token", new TokenEvent(done.answer()));
                send(emitter, lifecycle, "done", done);
                lifecycle.active.set(false);
                emitter.complete();
            } catch (RuntimeException error) {
                finishClaimedError(lifecycle, "pro_validation_failed");
            } finally {
                lifecycle.revoke();
            }
        }

        @Override
        public void onError(String code, String message) {
            String safe = "python_stream_interrupted".equals(code) ? "pro_stream_interrupted" : "pro_python_error";
            finishError(lifecycle, safe);
        }
    }

    private record TokenEvent(String content) {
    }

    private final class RunLifecycle {
        private final ProRunRegistry.RunCredentials run;
        private final SseEmitter emitter;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean terminal = new AtomicBoolean(false);
        private final AtomicBoolean revoked = new AtomicBoolean(false);
        private final AtomicReference<Future<?>> future = new AtomicReference<>();

        private RunLifecycle(ProRunRegistry.RunCredentials run, SseEmitter emitter) {
            this.run = run;
            this.emitter = emitter;
        }

        private void setFuture(Future<?> task) {
            future.set(task);
            if (!active.get()) {
                task.cancel(true);
            }
        }

        private boolean claimTerminal() {
            return terminal.compareAndSet(false, true);
        }

        private void cancel() {
            active.set(false);
            terminal.compareAndSet(false, true);
            Future<?> task = future.get();
            if (task != null) {
                task.cancel(true);
            }
            revoke();
        }

        private void revoke() {
            if (revoked.compareAndSet(false, true)) {
                try {
                    registry.revoke(run.runId(), run.token());
                } catch (RuntimeException ignored) {
                    // The TTL is the fallback when Redis is already unavailable or the run expired.
                }
            }
        }
    }

    /** Stable unavailable codes avoid routing a Pro request through Lite by accident. */
    public static class ProUnavailableException extends RuntimeException {
        public ProUnavailableException(String code) {
            super(code);
        }
    }
}
