package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantRecommendationItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantStreamDoneEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantStreamErrorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantStreamMetaEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantStreamTokenEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationDecision;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationDiagnostics;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationRecordCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Java 后端内部的 AI 网关。
 *
 * Java 负责会话、用户上下文、商品候选和消息落库；Python 只负责生成导购回答和推荐解释。
 */
@Service
public class AssistantService {

    private static final int TITLE_MAX_LENGTH = 30;

    private final ConversationApplicationService conversationService;
    private final AssistantContextService assistantContextService;
    private final AssistantRateLimitService assistantRateLimitService;
    private final PythonAssistantClient pythonAssistantClient;
    private final PythonAssistantStreamClient pythonAssistantStreamClient;
    private final AssistantFallbackService assistantFallbackService;
    private final ApplicationMetrics metrics;
    private final RecommendationAttributionService recommendationAttributionService;
    private final Executor assistantStreamingExecutor;
    private final long streamTimeoutMs;
    private final RecommendationDecisionService recommendationDecisionService = new RecommendationDecisionService();
    private final LegacyDemandIntentAdapter legacyDemandIntentAdapter = new LegacyDemandIntentAdapter();

    public AssistantService(
            ConversationApplicationService conversationService,
            AssistantContextService assistantContextService,
            AssistantRateLimitService assistantRateLimitService,
            PythonAssistantClient pythonAssistantClient,
            PythonAssistantStreamClient pythonAssistantStreamClient,
            AssistantFallbackService assistantFallbackService,
            ApplicationMetrics metrics,
            RecommendationAttributionService recommendationAttributionService,
            @Qualifier("assistantStreamingExecutor") Executor assistantStreamingExecutor,
            @Value("${app.ai.stream-timeout-ms:120000}") long streamTimeoutMs
    ) {
        this.conversationService = conversationService;
        this.assistantContextService = assistantContextService;
        this.assistantRateLimitService = assistantRateLimitService;
        this.pythonAssistantClient = pythonAssistantClient;
        this.pythonAssistantStreamClient = pythonAssistantStreamClient;
        this.assistantFallbackService = assistantFallbackService;
        this.metrics = metrics;
        this.recommendationAttributionService = recommendationAttributionService;
        this.assistantStreamingExecutor = assistantStreamingExecutor;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    public AssistantChatResponse chat(Long userId, AssistantChatRequest request) {
        assistantRateLimitService.assertAllowed(userId);
        String threadId = resolveThreadId(userId, request);
        String requestId = MDC.get("requestId");

        // 先保存用户消息，即使 Python 调用失败，也能在会话历史和日志中追踪用户原始问题。
        conversationService.appendMessage(userId, threadId, "user", request.message(), requestId);
        AssistantContext context;
        try {
            context = assistantContextService.buildContext(userId, threadId, request);
        } catch (RuntimeException exception) {
            return failedChatResponse(userId, threadId, requestId);
        }
        metrics.recordAiCandidateCount(context.candidates().size());
        if (hasText(context.clarificationQuestion())) {
            String answer = context.clarificationQuestion();
            conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
            AssembledRecommendation result = assembleResult(
                    userId, requestId, threadId, "sync", context, null, false, false);
            return toChatResponse(threadId, answer, context, result);
        }
        PythonChatRequest pythonRequest = toPythonRequest(userId, threadId, request, context);
        PythonCallResult pythonCall = callPythonOrFallback(pythonRequest);
        PythonChatResponse pythonResponse = pythonCall.response();

        String answer = requireAnswer(pythonResponse);
        conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);

        AssembledRecommendation result = assembleResult(
                userId, requestId, threadId, "sync", context, pythonResponse, true, pythonCall.dependencyFailed());
        return toChatResponse(threadId, answer, context, result);
    }

    /**
     * 创建前端 SSE 流并代理 Python `/chat/stream`。
     *
     * Java 在打开 Python 流之前完成会话归属校验、用户消息落库和候选商品装配；助手消息只在 Python done 后落库。
     *
     * @param userId 当前登录用户 ID
     * @param request 前端导购请求，沿用同步接口入参
     * @return Spring MVC SSE emitter，由后台执行器继续推送 token/done/error
     */
    public SseEmitter streamChat(Long userId, AssistantChatRequest request) {
        assistantRateLimitService.assertAllowed(userId);
        String threadId = resolveThreadId(userId, request);
        String requestId = MDC.get("requestId");

        conversationService.appendMessage(userId, threadId, "user", request.message(), requestId);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AtomicBoolean active = new AtomicBoolean(true);

        if (!sendEvent(emitter, active, "meta", new AssistantStreamMetaEvent(requestId, threadId))) {
            return emitter;
        }

        AssistantContext context;
        try {
            context = assistantContextService.buildContext(userId, threadId, request);
        } catch (RuntimeException exception) {
            completeFailedStream(userId, threadId, requestId, emitter, active);
            return emitter;
        }
        metrics.recordAiCandidateCount(context.candidates().size());
        PythonChatRequest pythonRequest = toPythonRequest(userId, threadId, request, context);

        if (hasText(context.clarificationQuestion())) {
            String answer = context.clarificationQuestion();
            conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
            AssembledRecommendation result = assembleResult(
                    userId, requestId, threadId, "stream", context, null, false, false);
            sendEvent(emitter, active, "token", new AssistantStreamTokenEvent(answer));
            sendEvent(emitter, active, "done", new AssistantStreamDoneEvent(
                    threadId, answer, result.recommendedSpuIds(), result.recommendedItems(), context.candidates().size(),
                    "demand_clarification", context.demandIntent(), result.status(),
                    result.recommendationId(), result.diagnostics()));
            emitter.complete();
            return emitter;
        }

        try {
            assistantStreamingExecutor.execute(() -> streamToPython(
                    pythonRequest,
                    new ForwardingStreamHandler(userId, threadId, requestId, context, emitter, active)
            ));
        } catch (RejectedExecutionException exception) {
            sendEvent(
                    emitter,
                    active,
                    "error",
                    new AssistantStreamErrorEvent("assistant_stream_busy", "AI assistant stream is busy")
            );
            emitter.complete();
        }
        return emitter;
    }

    private PythonCallResult callPythonOrFallback(PythonChatRequest pythonRequest) {
        try {
            PythonChatResponse pythonResponse = pythonAssistantClient.chat(pythonRequest);
            requireAnswer(pythonResponse);
            return new PythonCallResult(pythonResponse, false);
        } catch (RuntimeException exception) {
            metrics.recordAiFallback("sync");
            return new PythonCallResult(assistantFallbackService.chatFallbackResponse(pythonRequest), true);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveThreadId(Long userId, AssistantChatRequest request) {
        if (request.threadId() == null || request.threadId().isBlank()) {
            ConversationResponse conversation = conversationService.createConversation(userId, titleFrom(request.message()));
            return conversation.threadId();
        }
        String threadId = request.threadId().trim();
        // 复用旧会话时必须确认归属，不能让前端传入任意 threadId 续写他人上下文。
        conversationService.assertOwned(userId, threadId);
        return threadId;
    }

    private PythonChatRequest toPythonRequest(
            Long userId,
            String threadId,
            AssistantChatRequest request,
            AssistantContext context
    ) {
        return new PythonChatRequest(
                MDC.get("requestId"),
                threadId,
                threadId,
                request.message(),
                toPythonChatHistory(context.chatHistory()),
                toPythonUserContext(
                        userId,
                        context.profile(),
                        context.bodyData(),
                        context.preferences(),
                        context.behaviorSummary()
                ),
                toPythonCandidates(context.candidates()),
                context.effectiveDemand() == null
                        ? legacyDemandIntentAdapter.adapt(context.demandIntent())
                        : context.effectiveDemand(),
                false
        );
    }

    private List<PythonChatHistoryItem> toPythonChatHistory(List<MessageResponse> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<PythonChatHistoryItem> history = new java.util.ArrayList<>();
        String userQuery = null;
        for (MessageResponse message : messages) {
            if ("user".equals(message.role())) {
                userQuery = message.content();
            } else if ("assistant".equals(message.role()) && userQuery != null) {
                history.add(new PythonChatHistoryItem(userQuery, message.content()));
                userQuery = null;
            }
        }
        return history;
    }

    private PythonUserContext toPythonUserContext(
            Long userId,
            UserProfileResponse profile,
            UserBodyDataResponse bodyData,
            UserPreferencesResponse preferences,
            BehaviorSummaryResponse behaviorSummary
    ) {
        String gender = bodyData != null && bodyData.gender() != null ? bodyData.gender() : profile == null ? null : profile.gender();
        return new PythonUserContext(
                userId,
                bodyData == null ? null : bodyData.heightCm(),
                bodyData == null ? null : bodyData.weightKg(),
                gender,
                bodyData == null ? null : bodyData.preferredFit(),
                preferences == null ? List.of() : preferences.preferredStyles(),
                preferences == null ? List.of() : preferences.preferredColors(),
                preferences == null ? List.of() : preferences.dislikedColors(),
                preferences == null ? List.of() : preferences.preferredCategories(),
                preferences == null ? null : preferences.budgetMin(),
                preferences == null ? null : preferences.budgetMax(),
                behaviorSummary == null ? List.of() : behaviorSummary.recentInterestSpuIds(),
                behaviorSummary == null ? List.of() : behaviorSummary.recentCartSpuIds(),
                behaviorSummary == null ? List.of() : behaviorSummary.recentPurchasedSpuIds(),
                behaviorSummary == null ? List.of() : behaviorSummary.preferredCategories(),
                behaviorSummary == null ? List.of() : behaviorSummary.preferredStyles()
        );
    }

    private List<PythonProductCandidate> toPythonCandidates(List<RecommendationCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .map(candidate -> new PythonProductCandidate(
                        candidate.getSpuId(),
                        candidate.getSkuId(),
                        candidate.getName(),
                        candidate.getCategoryName(),
                        candidate.getSalePrice(),
                        candidate.getStockStatus(),
                        candidate.getColor(),
                        candidate.getSize(),
                        null,
                        firstCsvValue(candidate.getMaterials()),
                        candidate.getFitType(),
                        csvValues(candidate.getSeasons()),
                        csvValues(candidate.getStyleTags()),
                        candidate.getMainImageUrl(),
                        candidate.getSpuCode(),
                        candidate.getSkuCode(),
                        candidate.getAvailableStock(),
                        csvValues(candidate.getAttributeTags())
                ))
                .toList();
    }

    private String firstCsvValue(String csv) {
        return csvValues(csv).stream().findFirst().orElse(null);
    }

    private List<String> csvValues(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .filter(Objects::nonNull)
                .toList();
    }

    private String requireAnswer(PythonChatResponse pythonResponse) {
        if (pythonResponse == null || pythonResponse.answer() == null || pythonResponse.answer().isBlank()) {
            throw new ExternalServiceException("python assistant returned empty answer");
        }
        return pythonResponse.answer();
    }

    /**
     * Owns status, validated items, attribution and diagnostics for both synchronous and SSE completion paths.
     * A failure inside this boundary returns no recommendations so an earlier snapshot cannot leak into the turn.
     */
    private AssembledRecommendation assembleResult(
            Long userId,
            String requestId,
            String threadId,
            String mode,
            AssistantContext context,
            PythonChatResponse pythonResponse,
            boolean selectionAttempted,
            boolean dependencyFailed
    ) {
        List<PythonProductRef> selectedRefs = pythonResponse == null || pythonResponse.productRefs() == null
                ? List.of() : pythonResponse.productRefs();
        int candidateCount = context.candidates() == null ? 0 : context.candidates().size();
        try {
            RecommendationDecision decision = recommendationDecisionService.decide(
                    context.demandIntent(), context.candidates(), selectedRefs);
            List<AssistantRecommendationItem> acceptedItems = decision.recommendedItems();
            List<String> reasonCodes = diagnosticReasons(
                    candidateCount, selectedRefs.size(), acceptedItems.size(), selectionAttempted, dependencyFailed);
            RecommendationDiagnostics diagnostics = new RecommendationDiagnostics(
                    candidateCount, selectedRefs.size(), acceptedItems.size(),
                    decision.recommendationStatus(), reasonCodes);
            String recommendationId = recordRecommendation(
                    userId, requestId, threadId, mode, context, acceptedItems);
            recordDiagnostics(diagnostics);
            metrics.recordAiDiscardedReferences(decision.discardedReferences());
            return new AssembledRecommendation(
                    toRecommendedSpuIds(acceptedItems), acceptedItems,
                    decision.recommendationStatus(), recommendationId, diagnostics);
        } catch (RuntimeException exception) {
            return failedRecommendation(candidateCount, selectedRefs.size());
        }
    }

    private List<String> diagnosticReasons(
            int candidateCount,
            int selectedCount,
            int acceptedCount,
            boolean selectionAttempted,
            boolean dependencyFailed
    ) {
        List<String> reasons = new ArrayList<>();
        if (candidateCount == 0) {
            reasons.add("NO_JAVA_CANDIDATES");
        }
        if (dependencyFailed) {
            reasons.add("DEPENDENCY_FAILED");
        } else if (selectionAttempted && candidateCount > 0 && selectedCount == 0) {
            reasons.add("PYTHON_REJECTED_ALL");
        }
        if (selectedCount > 0 && acceptedCount == 0) {
            reasons.add("JAVA_DISCARDED_ALL_REFS");
        }
        return reasons;
    }

    private AssembledRecommendation failedRecommendation(int candidateCount, int selectedCount) {
        RecommendationDiagnostics diagnostics = new RecommendationDiagnostics(
                candidateCount, selectedCount, 0, RecommendationStatus.FAILED, List.of("DEPENDENCY_FAILED"));
        recordDiagnostics(diagnostics);
        return new AssembledRecommendation(
                List.of(), List.of(), RecommendationStatus.FAILED, null, diagnostics);
    }

    private void recordDiagnostics(RecommendationDiagnostics diagnostics) {
        metrics.recordAiSelection(
                diagnostics.javaCandidateCount(), diagnostics.pythonSelectedCount(),
                diagnostics.javaAcceptedCount(), diagnostics.status());
        diagnostics.reasonCodes().forEach(metrics::recordAiReasonCode);
    }

    private AssistantChatResponse toChatResponse(
            String threadId,
            String answer,
            AssistantContext context,
            AssembledRecommendation result
    ) {
        return new AssistantChatResponse(
                threadId, answer, result.recommendedSpuIds(), result.recommendedItems(),
                context.candidates().size(), context.demandIntent(), result.status(),
                result.recommendationId(), result.diagnostics());
    }

    private AssistantChatResponse failedChatResponse(Long userId, String threadId, String requestId) {
        String answer = assistantFallbackService.streamFallbackResponse(requestId).answer();
        conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
        AssembledRecommendation result = failedRecommendation(0, 0);
        return new AssistantChatResponse(
                threadId, answer, List.of(), List.of(), 0, DemandIntent.empty(null),
                result.status(), null, result.diagnostics());
    }

    private void completeFailedStream(
            Long userId,
            String threadId,
            String requestId,
            SseEmitter emitter,
            AtomicBoolean active
    ) {
        String answer = assistantFallbackService.streamFallbackResponse(requestId).answer();
        conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
        AssembledRecommendation result = failedRecommendation(0, 0);
        sendEvent(emitter, active, "done", new AssistantStreamDoneEvent(
                threadId, answer, List.of(), List.of(), 0, "assistant_failed", DemandIntent.empty(null),
                result.status(), null, result.diagnostics()));
        emitter.complete();
    }

    private List<Long> toRecommendedSpuIds(List<AssistantRecommendationItem> recommendedItems) {
        if (recommendedItems == null || recommendedItems.isEmpty()) {
            return List.of();
        }
        return recommendedItems.stream()
                .map(AssistantRecommendationItem::spuId)
                .distinct()
                .toList();
    }

    private String recordRecommendation(
            Long userId,
            String requestId,
            String threadId,
            String mode,
            AssistantContext context,
            List<AssistantRecommendationItem> recommendedItems
    ) {
        List<RecommendationRecordCommand.Item> candidates = context.candidates().stream()
                .map(candidate -> new RecommendationRecordCommand.Item(
                        candidate.getSpuId(), candidate.getSkuId(), null))
                .toList();
        List<RecommendationRecordCommand.Item> selectedItems = recommendedItems.stream()
                .map(item -> new RecommendationRecordCommand.Item(
                        item.spuId(), item.skuId(), item.rankScore()))
                .toList();
        return recommendationAttributionService.record(new RecommendationRecordCommand(
                userId, requestId, threadId, mode, candidates, selectedItems));
    }

    private String titleFrom(String message) {
        String title = message.trim();
        if (title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        // 第一版直接截取用户首问作为会话标题，后续可由 Python 生成更自然的标题。
        return title.substring(0, TITLE_MAX_LENGTH);
    }

    private boolean sendEvent(SseEmitter emitter, AtomicBoolean active, String eventName, Object data) {
        if (!active.get()) {
            return false;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
            return true;
        } catch (IOException exception) {
            active.set(false);
            emitter.complete();
            return false;
        }
    }

    private void streamToPython(PythonChatRequest pythonRequest, PythonAssistantStreamHandler handler) {
        try {
            pythonAssistantStreamClient.streamChat(pythonRequest, handler);
        } catch (RuntimeException exception) {
            AssistantStreamErrorEvent error = assistantFallbackService.streamFallbackError();
            handler.onError(error.code(), error.message());
        }
    }

    private record PythonCallResult(PythonChatResponse response, boolean dependencyFailed) {
    }

    private record AssembledRecommendation(
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            RecommendationStatus status,
            String recommendationId,
            RecommendationDiagnostics diagnostics
    ) {
    }

    private final class ForwardingStreamHandler implements PythonAssistantStreamHandler {
        private final Long userId;
        private final String threadId;
        private final String requestId;
        private final AssistantContext context;
        private final SseEmitter emitter;
        private final AtomicBoolean active;

        private ForwardingStreamHandler(
                Long userId,
                String threadId,
                String requestId,
                AssistantContext context,
                SseEmitter emitter,
                AtomicBoolean active
        ) {
            this.userId = userId;
            this.threadId = threadId;
            this.requestId = requestId;
            this.context = context;
            this.emitter = emitter;
            this.active = active;
        }

        @Override
        public void onToken(String content) {
            if (content == null || content.isEmpty()) {
                return;
            }
            sendEvent(emitter, active, "token", new AssistantStreamTokenEvent(content));
        }

        @Override
        public void onDone(PythonChatResponse response) {
            complete(response, false);
        }

        private void complete(PythonChatResponse response, boolean dependencyFailed) {
            if (!active.get()) {
                return;
            }
            String answer;
            try {
                answer = requireAnswer(response);
            } catch (RuntimeException exception) {
                response = assistantFallbackService.streamFallbackResponse(requestId);
                answer = response.answer();
                dependencyFailed = true;
            }
            conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
            AssembledRecommendation result = assembleResult(
                    userId, requestId, threadId, "stream", context, response, true, dependencyFailed);
            AssistantStreamDoneEvent done = new AssistantStreamDoneEvent(
                    threadId,
                    answer,
                    result.recommendedSpuIds(),
                    result.recommendedItems(),
                    context.candidates().size(),
                    response.intent(),
                    context.demandIntent(),
                    result.status(),
                    result.recommendationId(),
                    result.diagnostics()
            );
            if (sendEvent(emitter, active, "done", done)) {
                emitter.complete();
            }
        }

        @Override
        public void onError(String code, String message) {
            if (!active.get()) {
                return;
            }
            metrics.recordAiFallback("stream");
            complete(assistantFallbackService.streamFallbackResponse(requestId), true);
        }
    }
}
