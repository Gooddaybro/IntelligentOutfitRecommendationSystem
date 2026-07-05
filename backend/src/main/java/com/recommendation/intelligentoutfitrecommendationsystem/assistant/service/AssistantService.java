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
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
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
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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

    private final ConversationService conversationService;
    private final AssistantContextService assistantContextService;
    private final AssistantRateLimitService assistantRateLimitService;
    private final PythonAssistantClient pythonAssistantClient;
    private final PythonAssistantStreamClient pythonAssistantStreamClient;
    private final AssistantFallbackService assistantFallbackService;
    private final Executor assistantStreamingExecutor;
    private final long streamTimeoutMs;

    public AssistantService(
            ConversationService conversationService,
            AssistantContextService assistantContextService,
            AssistantRateLimitService assistantRateLimitService,
            PythonAssistantClient pythonAssistantClient,
            PythonAssistantStreamClient pythonAssistantStreamClient,
            AssistantFallbackService assistantFallbackService,
            @Qualifier("assistantStreamingExecutor") Executor assistantStreamingExecutor,
            @Value("${app.ai.stream-timeout-ms:120000}") long streamTimeoutMs
    ) {
        this.conversationService = conversationService;
        this.assistantContextService = assistantContextService;
        this.assistantRateLimitService = assistantRateLimitService;
        this.pythonAssistantClient = pythonAssistantClient;
        this.pythonAssistantStreamClient = pythonAssistantStreamClient;
        this.assistantFallbackService = assistantFallbackService;
        this.assistantStreamingExecutor = assistantStreamingExecutor;
        this.streamTimeoutMs = streamTimeoutMs;
    }

    public AssistantChatResponse chat(Long userId, AssistantChatRequest request) {
        assistantRateLimitService.assertAllowed(userId);
        String threadId = resolveThreadId(userId, request);
        String requestId = MDC.get("requestId");

        // 先保存用户消息，即使 Python 调用失败，也能在会话历史和日志中追踪用户原始问题。
        conversationService.appendMessage(userId, threadId, "user", request.message(), requestId);
        AssistantContext context = assistantContextService.buildContext(userId, threadId, request);
        PythonChatRequest pythonRequest = toPythonRequest(userId, threadId, request, context);
        PythonChatResponse pythonResponse = callPythonOrFallback(pythonRequest, request, context);

        String answer = requireAnswer(pythonResponse);
        conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);

        List<AssistantRecommendationItem> recommendedItems = toRecommendedItems(pythonResponse.productRefs(), context.candidates());
        return new AssistantChatResponse(
                threadId,
                answer,
                toRecommendedSpuIds(recommendedItems),
                recommendedItems,
                context.candidates().size()
        );
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
        AssistantContext context = assistantContextService.buildContext(userId, threadId, request);
        PythonChatRequest pythonRequest = toPythonRequest(userId, threadId, request, context);
        SseEmitter emitter = new SseEmitter(streamTimeoutMs);
        AtomicBoolean active = new AtomicBoolean(true);

        if (!sendEvent(emitter, active, "meta", new AssistantStreamMetaEvent(requestId, threadId))) {
            return emitter;
        }

        if (assistantFallbackService.shouldBypassPython()) {
            AssistantStreamErrorEvent error = assistantFallbackService.streamFallbackError();
            sendEvent(emitter, active, "error", error);
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

    private PythonChatResponse callPythonOrFallback(
            PythonChatRequest pythonRequest,
            AssistantChatRequest request,
            AssistantContext context
    ) {
        if (assistantFallbackService.shouldBypassPython()) {
            return assistantFallbackService.chatFallbackResponse(pythonRequest, request, context);
        }
        try {
            PythonChatResponse pythonResponse = pythonAssistantClient.chat(pythonRequest);
            requireAnswer(pythonResponse);
            assistantFallbackService.recordPythonSuccess();
            return pythonResponse;
        } catch (RuntimeException exception) {
            assistantFallbackService.recordPythonFailure(exception);
            return assistantFallbackService.chatFallbackResponse(pythonRequest, request, context);
        }
    }

    private String resolveThreadId(Long userId, AssistantChatRequest request) {
        if (request.threadId() == null || request.threadId().isBlank()) {
            ConversationResponse conversation = conversationService.createConversation(userId, titleFrom(request.message()));
            return conversation.threadId();
        }
        String threadId = request.threadId().trim();
        // 复用旧会话时必须确认归属，不能让前端传入任意 threadId 续写他人上下文。
        conversationService.requireConversation(userId, threadId);
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

    private List<AssistantRecommendationItem> toRecommendedItems(
            List<PythonProductRef> productRefs,
            List<RecommendationCandidate> candidates
    ) {
        if (productRefs == null || productRefs.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        return productRefs.stream()
                .filter(ref -> isKnownCandidateRef(ref, candidates))
                .filter(ref -> seen.add(ref.spuId() + ":" + ref.skuId()))
                .map(ref -> new AssistantRecommendationItem(
                        ref.spuId(),
                        ref.skuId(),
                        normalizeRecommendationReason(ref.reason()),
                        ref.rankScore()
                ))
                .toList();
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

    private String normalizeRecommendationReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "符合本轮 Java 候选商品条件。";
        }
        return reason.trim();
    }

    private boolean isKnownCandidateRef(PythonProductRef ref, List<RecommendationCandidate> candidates) {
        if (ref == null || ref.spuId() == null || ref.skuId() == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        return candidates.stream()
                .anyMatch(candidate -> ref.spuId().equals(candidate.getSpuId())
                        && ref.skuId().equals(candidate.getSkuId()));
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
            if (!active.get()) {
                return;
            }
            String answer;
            try {
                answer = requireAnswer(response);
            } catch (RuntimeException exception) {
                assistantFallbackService.recordPythonFailure(exception);
                AssistantStreamErrorEvent error = assistantFallbackService.streamFallbackError();
                sendEvent(emitter, active, "error", error);
                emitter.complete();
                return;
            }
            assistantFallbackService.recordPythonSuccess();
            conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);
            List<AssistantRecommendationItem> recommendedItems = toRecommendedItems(response.productRefs(), context.candidates());
            AssistantStreamDoneEvent done = new AssistantStreamDoneEvent(
                    threadId,
                    answer,
                    toRecommendedSpuIds(recommendedItems),
                    recommendedItems,
                    context.candidates().size(),
                    response.intent()
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
            assistantFallbackService.recordPythonFailure(null);
            AssistantStreamErrorEvent error = assistantFallbackService.streamFallbackError();
            sendEvent(emitter, active, "error", error);
            emitter.complete();
        }
    }
}
