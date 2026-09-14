package com.recommendation.intelligentoutfitrecommendationsystem.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.ProPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Independent Pro preparation and transport lifecycle, without Lite intent routing.
 * Task 3 produces an internal unvalidated result, never a persisted assistant answer.
 * Final validation and safe public result assembly belong to the later result stage.
 */
@Service
public class ProAssistantService {
    private final ConversationApplicationService conversations;
    private final AssistantRateLimitService limiter;
    private final ProContextService context;
    private final ProRunRegistry registry;
    private final ProPythonAssistantClient client;
    private final boolean enabled;

    public ProAssistantService(ConversationApplicationService conversations, AssistantRateLimitService limiter,
                               ProContextService context, ProRunRegistry registry, ProPythonAssistantClient client,
                               @Value("${app.ai.pro-enabled:false}") boolean enabled) {
        this.conversations = conversations;
        this.limiter = limiter;
        this.context = context;
        this.registry = registry;
        this.client = client;
        this.enabled = enabled;
    }

    /**
     * Saves an authorized user turn, exchanges v2 data, then revokes its tool credentials.
     * Callers must not expose or persist this return value without final business validation.
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
            return client.chat(context.build(userId, threadId, request, requestId, run));
        } finally {
            registry.revoke(run.runId(), run.token());
        }
    }

    /** Stable unavailable codes avoid routing a Pro request through Lite by accident. */
    public static class ProUnavailableException extends RuntimeException {
        public ProUnavailableException(String code) {
            super(code);
        }
    }
}
