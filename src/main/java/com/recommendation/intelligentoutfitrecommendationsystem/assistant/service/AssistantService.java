package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatHistoryItem;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private final PythonAssistantClient pythonAssistantClient;

    public AssistantService(
            ConversationService conversationService,
            AssistantContextService assistantContextService,
            PythonAssistantClient pythonAssistantClient
    ) {
        this.conversationService = conversationService;
        this.assistantContextService = assistantContextService;
        this.pythonAssistantClient = pythonAssistantClient;
    }

    public AssistantChatResponse chat(Long userId, AssistantChatRequest request) {
        String threadId = resolveThreadId(userId, request);
        String requestId = MDC.get("requestId");

        // 先保存用户消息，即使 Python 调用失败，也能在会话历史和日志中追踪用户原始问题。
        conversationService.appendMessage(userId, threadId, "user", request.message(), requestId);
        AssistantContext context = assistantContextService.buildContext(userId, threadId, request);
        PythonChatResponse pythonResponse = pythonAssistantClient.chat(toPythonRequest(userId, threadId, request, context));

        String answer = requireAnswer(pythonResponse);
        conversationService.appendMessage(userId, threadId, "assistant", answer, requestId);

        List<Long> recommendedSpuIds = pythonResponse.recommendedSpuIds() == null
                ? List.of()
                : pythonResponse.recommendedSpuIds();
        return new AssistantChatResponse(threadId, answer, recommendedSpuIds, context.candidates().size());
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
                toPythonUserContext(userId, context.profile(), context.bodyData(), context.preferences()),
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
            UserPreferencesResponse preferences
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
                preferences == null ? null : preferences.budgetMax()
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
                        candidate.getMainImageUrl()
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

    private String titleFrom(String message) {
        String title = message.trim();
        if (title.length() <= TITLE_MAX_LENGTH) {
            return title;
        }
        // 第一版直接截取用户首问作为会话标题，后续可由 Python 生成更自然的标题。
        return title.substring(0, TITLE_MAX_LENGTH);
    }
}
