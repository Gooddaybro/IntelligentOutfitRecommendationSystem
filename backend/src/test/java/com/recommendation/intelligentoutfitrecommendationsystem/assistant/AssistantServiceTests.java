package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.PythonAssistantStreamHandler;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.MatchedDimension;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonProductRef;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantFallbackService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantRateLimitService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentResolver;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.RecommendationAttributionService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.AiSelectionStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
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
        verify(applicationMetrics).recordAiSelection(1, 1, 1, AiSelectionStatus.STRONG_MATCH);
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
    void fourTurnRegressionPublishesSummerCasualCandidatesWithoutWinterDerivedWarmth() {
        ConversationDemandStateStore stateStore = mock(ConversationDemandStateStore.class);
        AtomicReference<ConversationDemandStateSnapshot> persisted = new AtomicReference<>();
        when(stateStore.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    ConversationDemandStateSnapshot base = persisted.get();
                    if (base == null) {
                        base = new ConversationDemandStateSnapshot(invocation.getArgument(6), null);
                    }
                    ConversationDemandStateSnapshot next = mutation.apply(base);
                    persisted.set(next);
                    return next;
                });
        when(stateStore.read(10L, "thread-four-turn")).thenAnswer(invocation -> persisted.get());
        DemandIntentStateService stateService = new DemandIntentStateService(stateStore);
        DemandIntentResolver resolver = new DemandIntentResolver();
        List<String> messages = List.of(
                "177 130 男性 冬天该怎么穿？",
                "女性呢？",
                "夏天呢？",
                "日常休闲"
        );
        for (int index = 0; index < messages.size(); index++) {
            String message = messages.get(index);
            AssistantChatRequest turnRequest = new AssistantChatRequest(
                    "thread-four-turn", message, null, null, null, null, null, null, null);
            stateService.applyResolution(
                    10L, "thread-four-turn", "turn-" + (index + 1), null,
                    resolver.resolvePatch(turnRequest), null, null, DemandIntent.empty(message));
        }
        EffectiveDemand effectiveDemand = stateService.read(10L, "thread-four-turn").effectiveDemand();
        List<RecommendationCandidate> candidates = List.of(
                new RecommendationCandidate(
                        3101L, 4101L, "SUMMER-TOP", "夏季亚麻短袖衬衫", "衬衫", null,
                        "regular", "白色", "M", "亚麻", "summer", "casual",
                        new BigDecimal("199"), "in_stock", new BigDecimal("199"), new BigDecimal("199"),
                        8, "SUMMER-TOP-M", 8, "风格:休闲,材质特征:透气"),
                new RecommendationCandidate(
                        3102L, 4102L, "SUMMER-BOTTOM", "夏季休闲短裤", "短裤", null,
                        "regular", "卡其色", "M", "棉", "summer", "casual",
                        new BigDecimal("159"), "in_stock", new BigDecimal("159"), new BigDecimal("159"),
                        9, "SUMMER-BOTTOM-M", 9, "风格:休闲,厚度:轻薄")
        );
        AssistantContext context = new AssistantContext(
                new UserProfileResponse(10L, "tester", null, null, null),
                new UserBodyDataResponse(10L, new BigDecimal("177"), new BigDecimal("65"), "female", null, null, null, null, "regular"),
                new UserPreferencesResponse(10L, List.of(), List.of(), List.of(), List.of(), null, null),
                null, List.of(), candidates, DemandIntentStateSnapshot.toLegacyIntent(effectiveDemand),
                effectiveDemand, null, true);
        AssistantChatRequest finalRequest = new AssistantChatRequest(
                "thread-four-turn", "日常休闲", null, null, null, null, null, null, null);
        when(assistantContextService.buildContext(10L, "thread-four-turn", finalRequest)).thenReturn(context);
        when(pythonAssistantClient.chat(any(PythonChatRequest.class))).thenReturn(new PythonChatResponse(
                "turn-4", "夏季休闲搭配已生成。", "recommendation",
                List.of(
                        new PythonProductRef(
                                3101L, 4101L, "透气休闲上装", BigDecimal.ONE,
                                List.of(new MatchedDimension("style", "CASUAL", "casual", "PRODUCT_STYLE_TAG")),
                                "TOP"),
                        new PythonProductRef(
                                3102L, 4102L, "轻薄休闲下装", BigDecimal.ONE,
                                List.of(new MatchedDimension("style", "CASUAL", "casual", "PRODUCT_STYLE_TAG")),
                                "BOTTOM")
                )));
        when(recommendationAttributionService.record(any())).thenReturn("rec-four-turn");
        MDC.put("requestId", "turn-4");

        AssistantChatResponse response = newAssistantService().chat(10L, finalRequest);

        assertThat(effectiveDemand.value("targetGender")).contains("FEMALE");
        assertThat(effectiveDemand.value("season")).contains("SUMMER");
        assertThat(effectiveDemand.constraints("style")).flatExtracting(item -> item.values()).contains("CASUAL");
        assertThat(effectiveDemand.softPreferences()).noneMatch(item ->
                item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
        assertThat(effectiveDemand.constraints("thermal")).noneMatch(item -> item.values().contains("WARM"));
        assertThat(response.diagnostics().javaCandidateCount()).isEqualTo(2);
        assertThat(response.recommendationStatus()).isNotEqualTo(RecommendationStatus.FAILED);
        assertThat(response.recommendedItems())
                .extracting("spuId", "skuId", "outfitRole")
                .containsExactlyInAnyOrder(
                        tuple(3101L, 4101L, "TOP"),
                        tuple(3102L, 4102L, "BOTTOM"));
        ArgumentCaptor<PythonChatRequest> requestCaptor = ArgumentCaptor.forClass(PythonChatRequest.class);
        verify(pythonAssistantClient).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().candidates()).hasSize(2);
        assertThat(requestCaptor.getValue().demandIntent().softPreferences()).noneMatch(item ->
                item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
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
        verify(applicationMetrics).recordAiSelection(1, 0, 0, AiSelectionStatus.BROWSE_FALLBACK);
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
        verify(applicationMetrics).recordAiSelection(0, 0, 0, AiSelectionStatus.FAILED);
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

    @Test
    void staleDerivedRemovalPublishesOnlyTheBoundedReasonAndMetric() {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_stale", "summer", null, null, null, null, null, null);
        AssistantContext context = new AssistantContext(
                null, null, null, null, List.of(), List.of(), DemandIntent.empty("summer"), null, null, true);
        when(assistantContextService.buildContext(10L, "th_stale", request)).thenReturn(context);
        when(pythonAssistantClient.chat(any())).thenReturn(new PythonChatResponse(
                "req-stale", "browse", "recommendation", List.of()));

        AssistantChatResponse response = newAssistantService().chat(10L, request);

        assertThat(response.diagnostics().reasonCodes())
                .contains("STALE_DERIVED_CONSTRAINT_REMOVED");
        verify(applicationMetrics).recordAiReasonCode("STALE_DERIVED_CONSTRAINT_REMOVED");
    }

    @Test
    void concurrentDoneAndErrorClaimExactlyOneTerminalSideEffect() throws Exception {
        AssistantChatRequest request = new AssistantChatRequest(
                "th_terminal_race", "recommend", null, null, null, null, null, null);
        AssistantContext context = new AssistantContext(null, null, null, null, List.of(), List.of());
        AtomicReference<PythonAssistantStreamHandler> handlerRef = new AtomicReference<>();
        when(assistantContextService.buildContext(10L, "th_terminal_race", request)).thenReturn(context);
        doAnswer(invocation -> {
            handlerRef.set(invocation.getArgument(1));
            return null;
        }).when(pythonAssistantStreamClient).streamChat(any(), any());
        newAssistantService().streamChat(10L, request);
        PythonAssistantStreamHandler handler = handlerRef.get();
        CyclicBarrier barrier = new CyclicBarrier(2);
        CountDownLatch finished = new CountDownLatch(2);
        Thread done = Thread.ofPlatform().start(() -> {
            await(barrier);
            handler.onDone(new PythonChatResponse("req-race", "done", "recommendation", List.of()));
            finished.countDown();
        });
        Thread error = Thread.ofPlatform().start(() -> {
            await(barrier);
            handler.onError("failed", "failed");
            finished.countDown();
        });

        finished.await();
        done.join();
        error.join();

        verify(conversationService, times(1)).appendMessage(
                eq(10L), eq("th_terminal_race"), eq("assistant"), any(), any());
        verify(applicationMetrics, times(1)).recordAiSelection(any(Integer.class), any(Integer.class),
                any(Integer.class), any(AiSelectionStatus.class));
        verify(recommendationAttributionService, times(1)).record(any());
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
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
