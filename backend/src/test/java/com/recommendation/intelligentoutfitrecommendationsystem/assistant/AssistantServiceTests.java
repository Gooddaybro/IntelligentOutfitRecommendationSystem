package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

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

    @Mock
    private PythonAssistantStreamClient pythonAssistantStreamClient;

    private final Executor directExecutor = Runnable::run;

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

        AssistantChatResponse response = newAssistantService().chat(10L, request);

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

    @Test
    void ignoresPythonProductRefsOutsideCurrentCandidates() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_existing",
                "recommend a jacket",
                "outerwear",
                null,
                null,
                null,
                null,
                null
        );
        AssistantContext context = new AssistantContext(
                null,
                null,
                null,
                List.of(),
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

        when(assistantContextService.buildContext(10L, "th_existing", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenReturn(new PythonChatResponse(
                        "req-ai-service-test",
                        "A jacket fits this request.",
                        "recommendation",
                        List.of(
                                new PythonProductRef(9999L, 8888L, "hallucinated product", null),
                                new PythonProductRef(1001L, 2001L, "known candidate", null)
                        )
                ));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.recommendedSpuIds()).containsExactly(1001L);
    }

    @Test
    void streamsPythonTokensAndStoresAssistantMessageOnlyAfterDone() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_stream_existing",
                "我身高175体重70kg，适合穿什么码？",
                "outerwear",
                "commute",
                "autumn",
                null,
                "regular",
                800
        );
        AssistantContext context = new AssistantContext(
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        AtomicReference<PythonAssistantStreamHandler> handlerRef = new AtomicReference<>();
        MDC.put("requestId", "req-stream-service-test");

        when(assistantContextService.buildContext(10L, "th_stream_existing", request)).thenReturn(context);
        org.mockito.Mockito.doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return null;
        }).when(pythonAssistantStreamClient).streamChat(any(PythonChatRequest.class), any(PythonAssistantStreamHandler.class));

        SseEmitter emitter = newAssistantService().streamChat(10L, request);

        assertThat(emitter).isNotNull();
        assertThat(handlerRef).hasValueSatisfying(handler -> {
            handler.onToken("我建议");
            handler.onDone(new PythonChatResponse(
                    "req-stream-service-test",
                    "我建议您穿 L 码。",
                    "size_recommendation",
                    List.of(new PythonProductRef(1001L, 2001L, "尺码匹配", null))
            ));
        });

        InOrder order = inOrder(conversationService, assistantContextService, pythonAssistantStreamClient);
        order.verify(conversationService).requireConversation(10L, "th_stream_existing");
        order.verify(conversationService).appendMessage(
                10L,
                "th_stream_existing",
                "user",
                "我身高175体重70kg，适合穿什么码？",
                "req-stream-service-test"
        );
        order.verify(assistantContextService).buildContext(10L, "th_stream_existing", request);
        order.verify(pythonAssistantStreamClient).streamChat(any(PythonChatRequest.class), any(PythonAssistantStreamHandler.class));
        order.verify(conversationService).appendMessage(
                10L,
                "th_stream_existing",
                "assistant",
                "我建议您穿 L 码。",
                "req-stream-service-test"
        );
    }

    private AssistantService newAssistantService() {
        return new AssistantService(
                conversationService,
                assistantContextService,
                pythonAssistantClient,
                pythonAssistantStreamClient,
                directExecutor,
                120_000L
        );
    }
}
