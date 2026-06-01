package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
                new UserBodyDataResponse(10L, new BigDecimal("175.5"), new BigDecimal("70.0"), "male", null, null, null, null, "regular"),
                new UserPreferencesResponse(10L, List.of("commute"), List.of("black"), List.of(), List.of("outerwear"), null, new BigDecimal("800.0")),
                List.of(
                        new MessageResponse("user", "上一轮的问题", "succeeded", "req-old", LocalDateTime.of(2026, 5, 28, 9, 30)),
                        new MessageResponse("assistant", "上一轮的回答", "succeeded", "req-old", LocalDateTime.of(2026, 5, 28, 9, 31))
                ),
                List.of(new RecommendationCandidate(
                        1001L,
                        2001L,
                        "SPU-1001",
                        "秋季男士通勤外套",
                        "外套",
                        null,
                        "regular",
                        "黑色",
                        "L",
                        "棉",
                        "autumn",
                        "commute",
                        new BigDecimal("299.0"),
                        "in_stock",
                        new BigDecimal("299.0"),
                        new BigDecimal("399.0"),
                        8
                ))
        );
        MDC.put("requestId", "req-ai-service-test");

        when(conversationService.createConversation(10L, "recommend a warm jacket")).thenReturn(conversation);
        when(assistantContextService.buildContext(10L, "th_service_001", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenReturn(new PythonChatResponse(
                        "req-ai-service-test",
                        "A wool blend jacket fits this request.",
                        "recommendation",
                        List.of(new PythonProductRef(1001L, 2001L, "fits the requested commute style", null))
                ));

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
        PythonChatRequest pythonRequest = captor.getValue();
        assertThat(pythonRequest.requestId()).isEqualTo("req-ai-service-test");
        assertThat(pythonRequest.sessionId()).isEqualTo("th_service_001");
        assertThat(pythonRequest.threadId()).isEqualTo("th_service_001");
        assertThat(pythonRequest.query()).isEqualTo("recommend a warm jacket");
        assertThat(pythonRequest.debug()).isFalse();
        assertThat(pythonRequest.chatHistory())
                .extracting("userQuery", "assistantAnswer")
                .containsExactly(tuple("上一轮的问题", "上一轮的回答"));
        assertThat(pythonRequest.userContext().userId()).isEqualTo(10L);
        assertThat(pythonRequest.userContext().heightCm()).isEqualByComparingTo("175.5");
        assertThat(pythonRequest.userContext().preferredStyles()).containsExactly("commute");
        assertThat(pythonRequest.userContext().budgetMax()).isEqualByComparingTo("800.0");
        assertThat(pythonRequest.candidates()).hasSize(1);
        assertThat(pythonRequest.candidates().get(0).spuId()).isEqualTo(1001L);
        assertThat(pythonRequest.candidates().get(0).skuId()).isEqualTo(2001L);
        assertThat(pythonRequest.candidates().get(0).salePrice()).isEqualByComparingTo("299.0");
        assertThat(pythonRequest.candidates().get(0).stockStatus()).isEqualTo("in_stock");
        assertThat(pythonRequest.candidates().get(0).color()).isEqualTo("黑色");
        assertThat(pythonRequest.candidates().get(0).size()).isEqualTo("L");
    }
}
