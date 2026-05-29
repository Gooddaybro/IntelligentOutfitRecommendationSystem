package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTests {

    @Mock
    private ConversationService conversationService;

    @Mock
    private AssistantContextService assistantContextService;

    @Mock
    private PythonAssistantClient pythonAssistantClient;

    @InjectMocks
    private AssistantService assistantService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void createsConversationCallsPythonAndStoresBothMessages() {
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "recommend a warm jacket",
                "outerwear",
                "commute",
                "autumn",
                null,
                "regular",
                600
        );
        ConversationResponse conversation = new ConversationResponse(
                "th_service_001",
                "recommend a warm jacket",
                "active",
                LocalDateTime.now(),
                null
        );
        AssistantContext context = new AssistantContext(
                new UserProfileResponse(10L, "tester", null, null, null),
                new UserBodyDataResponse(10L, null, null, null, null, null, null, null, null),
                new UserPreferencesResponse(10L, List.of("commute"), List.of(), List.of(), List.of(), null, null),
                List.of(),
                List.of()
        );
        MDC.put("requestId", "req-ai-service-test");

        when(conversationService.createConversation(10L, "recommend a warm jacket")).thenReturn(conversation);
        when(assistantContextService.buildContext(10L, "th_service_001", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenReturn(new PythonChatResponse("A wool blend jacket fits this request.", List.of(1001L)));

        AssistantChatResponse response = assistantService.chat(10L, request);

        assertThat(response.threadId()).isEqualTo("th_service_001");
        assertThat(response.answer()).contains("wool blend jacket");
        assertThat(response.recommendedSpuIds()).containsExactly(1001L);

        InOrder order = inOrder(conversationService, assistantContextService, pythonAssistantClient);
        order.verify(conversationService).createConversation(10L, "recommend a warm jacket");
        order.verify(conversationService).appendMessage(10L, "th_service_001", "user", "recommend a warm jacket", "req-ai-service-test");
        order.verify(assistantContextService).buildContext(10L, "th_service_001", request);
        order.verify(pythonAssistantClient).chat(any(PythonChatRequest.class));
        order.verify(conversationService).appendMessage(10L, "th_service_001", "assistant", "A wool blend jacket fits this request.", "req-ai-service-test");

        ArgumentCaptor<PythonChatRequest> captor = ArgumentCaptor.forClass(PythonChatRequest.class);
        verify(pythonAssistantClient).chat(captor.capture());
        assertThat(captor.getValue().threadId()).isEqualTo("th_service_001");
        assertThat(captor.getValue().userId()).isEqualTo(10L);
        assertThat(captor.getValue().message()).isEqualTo("recommend a warm jacket");
    }
}
