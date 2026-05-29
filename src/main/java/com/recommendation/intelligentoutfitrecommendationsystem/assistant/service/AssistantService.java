package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.List;

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
                threadId,
                userId,
                request.message(),
                context.profile(),
                context.bodyData(),
                context.preferences(),
                context.chatHistory(),
                context.candidates()
        );
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
