package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.MatchedDimension;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantFallbackService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantRateLimitService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantServiceTests {

    @Mock
    private ConversationApplicationService conversationService;

    @Mock
    private AssistantContextService assistantContextService;

    @Mock
    private AssistantRateLimitService assistantRateLimitService;

    @Mock
    private PythonAssistantClient pythonAssistantClient;

    @Mock
    private PythonAssistantStreamClient pythonAssistantStreamClient;

    @Mock
    private ApplicationMetrics applicationMetrics;

    @Mock
    private RecommendationAttributionService recommendationAttributionService;

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
        BehaviorSummaryResponse behaviorSummary = new BehaviorSummaryResponse(
                List.of(1001L),
                List.of(1002L),
                List.of(1003L),
                List.of("外套"),
                List.of("commute"),
                List.of()
        );
        AssistantContext context = new AssistantContext(
                new UserProfileResponse(10L, "tester", null, null, null),
                new UserBodyDataResponse(10L, new BigDecimal("175.5"), new BigDecimal("70.0"), "male", null, null, null, null, "regular"),
                new UserPreferencesResponse(10L, List.of("commute"), List.of("black"), List.of(), List.of("outerwear"), null, new BigDecimal("800.0")),
                behaviorSummary,
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
                        8,
                        "JACKET-COMMUTE-BLK-L",
                        8,
                        "场景:通勤,风格:百搭,厚度:轻薄,搭配难度:好搭"
                )),
                demandIntent("外套", "autumn", List.of("commute"))
        );
        MDC.put("requestId", "req-ai-service-test");

        when(conversationService.createConversation(10L, "recommend a warm jacket")).thenReturn(conversation);
        when(assistantContextService.buildContext(10L, "th_service_001", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenReturn(new PythonChatResponse(
                        "req-ai-service-test",
                        "A wool blend jacket fits this request.",
                        "recommendation",
                        List.of(new PythonProductRef(
                                1001L, 2001L, "fits the requested commute style", null,
                                List.of(new MatchedDimension(
                                        "style", "commute", "commute", "PRODUCT_STYLE_TAG"))
                        ))
                ));
        when(recommendationAttributionService.record(any())).thenReturn("rec_sync_test");

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.threadId()).isEqualTo("th_service_001");
        assertThat(response.answer()).contains("wool blend jacket");
        assertThat(response.recommendedSpuIds()).containsExactly(1001L);
        assertThat(response.recommendationId()).isEqualTo("rec_sync_test");
        assertThat(response.recommendationStatus()).isEqualTo(RecommendationStatus.STRONG_MATCH);
        assertThat(response.diagnostics().javaCandidateCount()).isEqualTo(1);
        assertThat(response.diagnostics().pythonSelectedCount()).isEqualTo(1);
        assertThat(response.diagnostics().javaAcceptedCount()).isEqualTo(1);
        assertThat(response.diagnostics().status()).isEqualTo(RecommendationStatus.STRONG_MATCH);
        assertThat(response.diagnostics().reasonCodes()).isEmpty();
        assertThat(response.recommendedItems())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(tuple(1001L, 2001L, "fits the requested commute style"));
        verify(applicationMetrics).recordAiCandidateCount(1);
        verify(applicationMetrics).recordAiSelection(1, 1, 1, RecommendationStatus.STRONG_MATCH);
        verify(recommendationAttributionService).record(argThat(command ->
                "sync".equals(command.mode())
                        && command.candidates().size() == 1
                        && command.selectedItems().size() == 1
                        && Long.valueOf(2001L).equals(command.selectedItems().getFirst().skuId())
        ));

        InOrder order = inOrder(conversationService, assistantContextService, pythonAssistantClient);
        verify(assistantRateLimitService).assertAllowed(10L);
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
        assertThat(pythonRequest.userContext().recentInterestSpuIds()).containsExactly(1001L);
        assertThat(pythonRequest.userContext().recentCartSpuIds()).containsExactly(1002L);
        assertThat(pythonRequest.userContext().recentPurchasedSpuIds()).containsExactly(1003L);
        assertThat(pythonRequest.userContext().behaviorPreferredCategories()).containsExactly("外套");
        assertThat(pythonRequest.userContext().behaviorPreferredStyles()).containsExactly("commute");
        assertThat(pythonRequest.candidates()).hasSize(1);
        assertThat(pythonRequest.candidates().get(0).spuId()).isEqualTo(1001L);
        assertThat(pythonRequest.candidates().get(0).skuId()).isEqualTo(2001L);
        assertThat(pythonRequest.candidates().get(0).salePrice()).isEqualByComparingTo("299.0");
        assertThat(pythonRequest.candidates().get(0).stockStatus()).isEqualTo("in_stock");
        assertThat(pythonRequest.candidates().get(0).color()).isEqualTo("黑色");
        assertThat(pythonRequest.candidates().get(0).size()).isEqualTo("L");
        assertThat(pythonRequest.candidates().get(0).spuCode()).isEqualTo("SPU-1001");
        assertThat(pythonRequest.candidates().get(0).skuCode()).isEqualTo("JACKET-COMMUTE-BLK-L");
        assertThat(pythonRequest.candidates().get(0).availableStock()).isEqualTo(8);
        assertThat(pythonRequest.candidates().get(0).attributeTags())
                .containsExactly("场景:通勤", "风格:百搭", "厚度:轻薄", "搭配难度:好搭");
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
                        8,
                        "JACKET-COMMUTE-BLK-L",
                        8,
                        "适用场景:通勤"
                )),
                demandIntent("外套", "autumn", List.of("commute"))
        );

        when(assistantContextService.buildContext(10L, "th_existing", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenReturn(new PythonChatResponse(
                        "req-ai-service-test",
                        "A jacket fits this request.",
                        "recommendation",
                        List.of(
                                new PythonProductRef(
                                        9999L, 8888L, "hallucinated product", null,
                                        List.of(new MatchedDimension(
                                                "style", "commute", "commute", "PRODUCT_STYLE_TAG"))
                                ),
                                new PythonProductRef(
                                        1001L, 2001L, "known candidate", null,
                                        List.of(new MatchedDimension(
                                                "style", "commute", "commute", "PRODUCT_STYLE_TAG"))
                                )
                        )
                ));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.recommendedSpuIds()).containsExactly(1001L);
        assertThat(response.recommendationStatus()).isEqualTo(RecommendationStatus.STRONG_MATCH);
        verify(assistantRateLimitService).assertAllowed(10L);
        assertThat(response.recommendedItems())
                .extracting("spuId", "skuId", "reason")
                .containsExactly(tuple(1001L, 2001L, "known candidate"));
    }

    private DemandIntent demandIntent(String category, String season, List<String> style) {
        return new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "test", "PRODUCT_RECOMMENDATION",
                List.of("PRODUCT_SELECTION"), null, category, season, List.of(), style, List.of(),
                null, List.of(), null, List.of("category", "season"), List.of("style"),
                new BigDecimal("0.80"), List.of()
        );
    }

    @Test
    void chatReturnsSafeFallbackAndStoresAssistantMessageWhenPythonFails() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_existing",
                "recommend a jacket",
                "outerwear",
                "commute",
                null,
                null,
                "regular",
                null
        );
        AssistantContext context = new AssistantContext(
                null,
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
                        8,
                        "JACKET-COMMUTE-BLK-L",
                        8,
                        "适用场景:通勤"
                ))
        );
        MDC.put("requestId", "req-ai-fallback-test");

        when(assistantContextService.buildContext(10L, "th_existing", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenThrow(new ExternalServiceException("provider timeout: /secret/path api-key sk-live-secret"));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.threadId()).isEqualTo("th_existing");
        assertThat(response.answer())
                .contains("AI 导购暂时不可用")
                .doesNotContain("provider")
                .doesNotContain("secret")
                .doesNotContain("/secret/path")
                .doesNotContain("api-key");
        assertThat(response.recommendedSpuIds()).isEmpty();
        assertThat(response.recommendedItems()).isEmpty();
        assertThat(response.candidatesCount()).isEqualTo(1);
        assertThat(response.recommendationStatus()).isEqualTo(RecommendationStatus.BROWSE_FALLBACK);
        assertThat(response.diagnostics().reasonCodes()).containsExactly("DEPENDENCY_FAILED");
        verify(applicationMetrics).recordAiSelection(1, 0, 0, RecommendationStatus.BROWSE_FALLBACK);
        verify(applicationMetrics).recordAiReasonCode("DEPENDENCY_FAILED");
        verify(assistantRateLimitService).assertAllowed(10L);
        verify(conversationService).appendMessage(10L, "th_existing", "user", "recommend a jacket", "req-ai-fallback-test");
        verify(conversationService).appendMessage(10L, "th_existing", "assistant", response.answer(), "req-ai-fallback-test");
        verify(applicationMetrics).recordAiFallback("sync");
    }

    @Test
    void candidateSnapshotFailureReturnsFailedWithoutReusingRecommendations() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_snapshot_failed", "recommend a jacket", null, null, null, null, null, null);
        MDC.put("requestId", "req-snapshot-failed");
        when(assistantContextService.buildContext(10L, "th_snapshot_failed", request))
                .thenThrow(new ExternalServiceException("candidate snapshot unavailable"));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.recommendationStatus()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(response.recommendedSpuIds()).isEmpty();
        assertThat(response.recommendedItems()).isEmpty();
        assertThat(response.diagnostics().javaCandidateCount()).isZero();
        assertThat(response.diagnostics().reasonCodes()).containsExactly("DEPENDENCY_FAILED");
        verify(applicationMetrics).recordAiSelection(0, 0, 0, RecommendationStatus.FAILED);
        verify(applicationMetrics).recordAiReasonCode("DEPENDENCY_FAILED");
        verify(pythonAssistantClient, never()).chat(any());
        verify(recommendationAttributionService, never()).record(any());
    }

    @Test
    void returnsSafeFallbackWhenResilientClientRejectsCall() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_existing",
                "recommend a jacket",
                "outerwear",
                "commute",
                null,
                null,
                "regular",
                null
        );
        AssistantContext context = new AssistantContext(
                null,
                null,
                null,
                null,
                List.of(),
                List.of()
        );
        when(assistantContextService.buildContext(10L, "th_existing", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
                .thenThrow(new ExternalServiceException("python circuit is open"));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.answer()).contains("AI 导购暂时不可用");
        assertThat(response.recommendedItems()).isEmpty();
        verify(pythonAssistantClient).chat(any(PythonChatRequest.class));
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
                null,
                List.of(),
                List.of()
        );
        AtomicReference<PythonAssistantStreamHandler> handlerRef = new AtomicReference<>();
        MDC.put("requestId", "req-stream-service-test");

        when(assistantContextService.buildContext(10L, "th_stream_existing", request)).thenReturn(context);
        when(recommendationAttributionService.record(any())).thenReturn("rec_stream_test");
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
        verify(assistantRateLimitService).assertAllowed(10L);
        order.verify(conversationService).assertOwned(10L, "th_stream_existing");
        order.verify(conversationService).appendMessage(
                10L,
                "th_stream_existing",
                "user",
                "我身高175体重70kg，适合穿什么码？",
                "req-stream-service-test"
        );
        verify(recommendationAttributionService).record(argThat(command ->
                "stream".equals(command.mode()) && "req-stream-service-test".equals(command.requestId())
        ));
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

    @Test
    void streamErrorCompletesWithSafeCandidateFallback() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_stream_error",
                "推荐一件通勤外套",
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
                null,
                List.of(),
                List.of()
        );
        AtomicReference<PythonAssistantStreamHandler> handlerRef = new AtomicReference<>();
        MDC.put("requestId", "req-stream-error-test");

        when(assistantContextService.buildContext(10L, "th_stream_error", request)).thenReturn(context);
        org.mockito.Mockito.doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return null;
        }).when(pythonAssistantStreamClient).streamChat(any(PythonChatRequest.class), any(PythonAssistantStreamHandler.class));

        SseEmitter emitter = newAssistantService().streamChat(10L, request);

        assertThat(emitter).isNotNull();
        assertThat(handlerRef).hasValueSatisfying(handler ->
                handler.onError("python_stream_error", "AI assistant stream failed")
        );
        verify(assistantRateLimitService).assertAllowed(10L);
        verify(conversationService).appendMessage(
                10L,
                "th_stream_error",
                "user",
                "推荐一件通勤外套",
                "req-stream-error-test"
        );
        verify(conversationService).appendMessage(
                eq(10L),
                eq("th_stream_error"),
                eq("assistant"),
                argThat(answer -> answer.contains("仍可继续浏览当前条件筛选出的商品")),
                eq("req-stream-error-test")
        );
    }

    @Test
    void streamReturnsSafeFallbackWhenResilientClientRejectsCall() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_stream_guard",
                "推荐一件通勤外套",
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
                null,
                List.of(),
                List.of()
        );
        MDC.put("requestId", "req-stream-guard-test");

        when(assistantContextService.buildContext(10L, "th_stream_guard", request)).thenReturn(context);
        doAnswer(invocation -> {
            invocation.<PythonAssistantStreamHandler>getArgument(1)
                    .onError("python_circuit_open", "python assistant circuit is open");
            return null;
        }).when(pythonAssistantStreamClient).streamChat(any(), any());

        SseEmitter emitter = newAssistantService().streamChat(10L, request);

        assertThat(emitter).isNotNull();
        verify(pythonAssistantStreamClient).streamChat(
                any(PythonChatRequest.class),
                any(PythonAssistantStreamHandler.class)
        );
        verify(conversationService).appendMessage(
                10L,
                "th_stream_guard",
                "user",
                "推荐一件通勤外套",
                "req-stream-guard-test"
        );
        verify(conversationService).appendMessage(
                eq(10L),
                eq("th_stream_guard"),
                eq("assistant"),
                argThat(answer -> answer.contains("仍可继续浏览当前条件筛选出的商品")),
                eq("req-stream-guard-test")
        );
    }

    private AssistantService newAssistantService() {
        return new AssistantService(
                conversationService,
                assistantContextService,
                assistantRateLimitService,
                pythonAssistantClient,
                pythonAssistantStreamClient,
                new AssistantFallbackService(),
                applicationMetrics,
                recommendationAttributionService,
                directExecutor,
                120_000L
        );
    }
}
