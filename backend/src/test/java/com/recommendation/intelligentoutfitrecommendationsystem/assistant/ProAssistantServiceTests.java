package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.ProPythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProPythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantRateLimitService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProAssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProAssistantServiceTests {
    private final ConversationApplicationService conversations = mock(ConversationApplicationService.class);
    private final AssistantRateLimitService limiter = mock(AssistantRateLimitService.class);
    private final ProContextService context = mock(ProContextService.class);
    private final ProRunRegistry registry = mock(ProRunRegistry.class);
    private final ProPythonAssistantClient client = mock(ProPythonAssistantClient.class);
    private final ProChatRequest request = new ProChatRequest("th-1", "推荐通勤外套，还想知道尺码", "pro",
            null, null, null, null, null, null, null);
    private final ProRunRegistry.RunCredentials run = new ProRunRegistry.RunCredentials("run-1", "run-secret");

    private ProAssistantService service(boolean enabled) {
        return new ProAssistantService(conversations, limiter, context, registry, client, enabled);
    }

    private void ready() throws Exception {
        when(registry.create(eq(7L), eq("th-1"), anyString())).thenReturn(run);
        when(context.build(eq(7L), eq("th-1"), eq(request), anyString(), eq(run)))
                .thenAnswer(call -> new ProPythonChatRequest("assistant-v2", "pro", call.getArgument(3),
                        "run-1", "th-1", request.message(), List.of(), Map.of("user_id", 7L), Map.of(), List.of(), "run-secret"));
        when(client.chat(any())).thenReturn(new ObjectMapper().readTree("{\"answer\":\"unvalidated\"}"));
    }

    @Test
    void disabledProHasNoSideEffectsOrLiteFallback() {
        assertThrows(ProAssistantService.ProUnavailableException.class,
                () -> service(false).exchangeForValidation(7L, request));
        verifyNoInteractions(conversations, limiter, context, registry, client);
    }

    @Test
    void crossUserConversationFailsBeforeMessageAndContext() {
        doThrow(new ResourceNotFoundException("not owned")).when(conversations).assertOwned(7L, "th-1");
        assertThrows(ResourceNotFoundException.class, () -> service(true).exchangeForValidation(7L, request));
        verifyNoInteractions(context, registry, client);
        verify(conversations, never()).appendMessage(any(), any(), any(), any(), any());
    }

    @Test
    void ownershipThenUserPersistenceThenContextAndV2CallThenRevoke() throws Exception {
        ready();
        var result = service(true).exchangeForValidation(7L, request);
        assertThat(result.path("answer").asText()).isEqualTo("unvalidated");
        var order = inOrder(conversations, registry, context, client);
        order.verify(conversations).assertOwned(7L, "th-1");
        order.verify(conversations).appendMessage(eq(7L), eq("th-1"), eq("user"), eq(request.message()), anyString());
        order.verify(registry).create(eq(7L), eq("th-1"), anyString());
        order.verify(context).build(eq(7L), eq("th-1"), eq(request), anyString(), eq(run));
        order.verify(client).chat(any());
        order.verify(registry).revoke("run-1", "run-secret");
        verify(conversations, never()).appendMessage(any(), any(), eq("assistant"), any(), any());
    }

    @Test
    void upstreamFailureStillRevokesAndDoesNotPersistAnAnswer() throws Exception {
        ready();
        when(client.chat(any())).thenThrow(new ExternalServiceException("upstream unavailable"));
        assertThrows(ExternalServiceException.class, () -> service(true).exchangeForValidation(7L, request));
        verify(registry).revoke("run-1", "run-secret");
        verify(conversations, never()).appendMessage(any(), any(), eq("assistant"), any(), any());
    }

    @Test
    void contextFailureStillRevokes() {
        when(registry.create(eq(7L), eq("th-1"), anyString())).thenReturn(run);
        when(context.build(eq(7L), eq("th-1"), eq(request), anyString(), eq(run)))
                .thenThrow(new IllegalStateException("profile unavailable"));
        assertThrows(IllegalStateException.class, () -> service(true).exchangeForValidation(7L, request));
        verify(registry).revoke("run-1", "run-secret");
        verifyNoInteractions(client);
    }
}
