package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.DemandIntentParseClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandSlots;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SlotEvidence;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOperator;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintOrigin;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ConstraintStrength;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.EffectiveDemand;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.IntentConstraint;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AssistantContextServiceTests {

    @Test
    void candidateQueryUsesOnlyHardEffectiveDemandAndKeepsSoftStyleForPython() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        EffectiveDemand effective = EffectiveDemand.v3(
                "\u65e5\u5e38\u4f11\u95f2", "OUTFIT_ADVICE", List.of("PRODUCT_SELECTION"),
                List.of(
                        constraint("gender", "targetGender", ConstraintOperator.EQUALS,
                                "FEMALE", ConstraintStrength.HARD),
                        constraint("season", "season", ConstraintOperator.EQUALS,
                                "SUMMER", ConstraintStrength.HARD)),
                List.of(
                        constraint("style", "style", ConstraintOperator.CONTAINS,
                                "CASUAL", ConstraintStrength.SOFT),
                        constraint("material", "material", ConstraintOperator.CONTAINS,
                                "COTTON", ConstraintStrength.SOFT),
                        constraint("fit", "fit", ConstraintOperator.CONTAINS,
                                "LOOSE", ConstraintStrength.SOFT)), null);
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(states.applyResolution(anyLong(), anyString(), anyString(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(new DemandIntentStateSnapshot(effective, null));
        when(parser.parse(any())).thenReturn(Optional.empty());
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-hard-only",
                new AssistantChatRequest("thread-hard-only", "\u65e5\u5e38\u4f11\u95f2",
                        null, null, null, null, null, null, null));

        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getGender()).isEqualTo("female");
        assertThat(query.getValue().getSeason()).isEqualTo("summer");
        assertThat(query.getValue().getStyle()).isNull();
        assertThat(query.getValue().getMaterial()).isNull();
        assertThat(query.getValue().getFit()).isNull();
        assertThat(context.effectiveDemand()).isEqualTo(effective);
        assertThat(context.effectiveDemand().softPreferences())
                .extracting(IntentConstraint::field).contains("style");
    }

    @Test
    void explicitRequestStyleRemainsAnExactCandidateFilter() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(profiles, candidates, conversations);
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(candidates.findCandidates(any())).thenReturn(List.of());

        service.buildContext(10L, "thread-explicit-style",
                new AssistantChatRequest("thread-explicit-style", "show options",
                        null, "casual", null, "cotton", "loose", null, null));

        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getStyle()).isEqualTo("casual");
        assertThat(query.getValue().getMaterial()).isEqualTo("cotton");
        assertThat(query.getValue().getFit()).isEqualTo("loose");
    }

    @Test
    void semanticParserFillsOnlyValidatedUnresolvedStyle() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        AssistantChatRequest request = new AssistantChatRequest(
                "thread-semantic", "给女朋友找成熟硬朗外套", null, null, null, null, null, null, null);
        LlmDemandParseResponse response = new LlmDemandParseResponse(
                "1.0", "MERGE", new LlmDemandSlots(null, null, null,
                List.of("MATURE", "RUGGED"), null, null),
                Map.of("style", new BigDecimal("0.81")),
                Map.of("style", List.of(
                        new SlotEvidence("成熟", "CURRENT_MESSAGE"),
                        new SlotEvidence("硬朗", "CURRENT_MESSAGE"))),
                false, null, null);
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, request.message(), "female", "外套",
                List.of(), List.of("mature", "rugged"), null, List.of(),
                List.of("targetGender", "category"), List.of("style"),
                new BigDecimal("0.80"), List.of());
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(response));
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(snapshot(effective, null));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-semantic", request);

        assertThat(context.demandIntent().style()).containsExactly("mature", "rugged");
        assertThat(context.clarificationQuestion()).isNull();
        verify(parser).parse(any());
        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getStyle()).isNull();
    }

    @Test
    void objectOuterwearCreatesPendingClarificationWithoutChangingGenderSqlFilter() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        AssistantChatRequest request = new AssistantChatRequest(
                "thread-object", "给对象买外套", null, null, null, null, null, null, null);
        LlmDemandParseResponse response = new LlmDemandParseResponse(
                "1.0", "CLARIFY", new LlmDemandSlots(null, null, null, null, null, null),
                Map.of(), Map.of(), true, "targetGender", "FEMALE", "确认要筛选女士商品吗？");
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", null, "确认要筛选女士商品吗？", request.message(), "req-object");
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, request.message(), null, "外套",
                List.of(), List.of(), null, List.of(), List.of("category"), List.of(),
                new BigDecimal("0.80"), List.of());
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(response));
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(snapshot(effective, pending));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-object", request);

        assertThat(context.clarificationQuestion()).isEqualTo("确认要筛选女士商品吗？");
        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getGender()).isNull();
        assertThat(query.getValue().getCategory()).isEqualTo("外套");
        verify(states).applyResolution(anyLong(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.eq("clarify"), any(), any(), any(), any());
    }

    @Test
    void explicitFemaleAnswerReplacesObjectPendingWithFormalFemaleDemand() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", null, "确认要筛选女士商品吗？", "给对象买外套", "req-object");
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "女朋友", "female", "外套",
                List.of(), List.of(), null, List.of(), List.of("targetGender", "category"), List.of(),
                new BigDecimal("0.80"), List.of());
        when(states.read(10L, "thread-object"))
                .thenReturn(snapshot(emptyIntent("给对象买外套"), pending));
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(snapshot(effective, null));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-object",
                new AssistantChatRequest("thread-object", "女朋友", null, null, null, null, null, null, null));

        assertThat(context.demandIntent().targetGender()).isEqualTo("female");
        assertThat(context.clarificationQuestion()).isNull();
        verify(parser, never()).parse(any());
        verify(states).applyResolution(anyLong(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.eq("merge"), any(), any(), any(), any());
        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getGender()).isEqualTo("female");
    }

    @Test
    void deterministicMaleDemandSurvivesParserMissForMovieHeroStyle() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        AssistantChatRequest request = new AssistantChatRequest(
                "thread-movie", "男性穿搭，像电影主角那种", null, null, null, null, null, null, null);
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, request.message(), "male", null,
                List.of(), List.of(), null, List.of(), List.of("targetGender"), List.of(),
                new BigDecimal("0.80"), List.of());
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.empty());
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(snapshot(effective, null));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-movie", request);

        assertThat(context.demandIntent().targetGender()).isEqualTo("male");
        assertThat(context.clarificationQuestion()).isNull();
        ArgumentCaptor<RecommendationCandidateQuery> query =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(query.capture());
        assertThat(query.getValue().getGender()).isEqualTo("male");
        assertThat(query.getValue().getStyle()).isNull();
    }

    @Test
    void candidateQueryUsesPersistedEffectiveDemandInsteadOfLatestPatchAlone() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidateService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        DemandIntentStateService demandIntentStateService = mock(DemandIntentStateService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService, candidateService, conversationService, mock(BehaviorSummaryService.class), demandIntentStateService);
        AssistantChatRequest request = new AssistantChatRequest(
                "thread-switch", "那女性呢？", null, null, null, null, null, null, null);
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, "那女性呢？", "female", "外套",
                List.of("commute"), List.of("minimal"), 500, List.of(),
                List.of("targetGender", "category", "budgetMax"), List.of("scene", "style"),
                new BigDecimal("0.80"), List.of());
        when(demandIntentStateService.apply(anyLong(), anyString(), any(), any(), any(), any()))
                .thenReturn(effective);
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(candidateService.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-switch", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidateService).findCandidates(captor.capture());
        assertThat(context.demandIntent()).isEqualTo(effective);
        assertThat(captor.getValue().getGender()).isEqualTo("female");
        assertThat(captor.getValue().getCategory()).isEqualTo("外套");
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(500);
    }

    @Test
    void extractsBudgetMaxFromNaturalLanguageWhenRequestFilterIsMissing() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                mock(BehaviorSummaryService.class)
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "学生党想要平价百搭，预算500以内",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-budget", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().budgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().hardFilters()).contains("budgetMax");
    }

    @Test
    void explicitBudgetMaxWinsOverMessageBudget() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                mock(BehaviorSummaryService.class)
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "预算500以内",
                null,
                null,
                null,
                null,
                null,
                300
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-budget", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(300);
    }

    @Test
    void messageMaleDemandSetsRecommendationGenderToMale() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                mock(BehaviorSummaryService.class)
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "男生 显高显瘦",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("male");
        assertThat(context.demandIntent().targetGender()).isEqualTo("male");
        assertThat(context.demandIntent().hardFilters()).containsExactly("targetGender");
    }

    @Test
    void messageSkirtDemandSetsRecommendationCategoryToSkirtCategory() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "女性裙子推荐",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-category", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("半裙");
        assertThat(context.demandIntent().targetGender()).isEqualTo("female");
        assertThat(context.demandIntent().category()).isEqualTo("半裙");
        assertThat(context.demandIntent().hardFilters()).containsExactly("targetGender", "category");
    }

    @Test
    void naturalLanguageCommuteStyleStaysOutOfExactCandidateFilter() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "女生上班通勤，预算500以内",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-intent", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("female");
        assertThat(captor.getValue().getStyle()).isNull();
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().targetGender()).isEqualTo("female");
        assertThat(context.demandIntent().scene()).containsExactly("commute");
        assertThat(context.demandIntent().style()).containsExactly("commute", "minimal");
        assertThat(context.demandIntent().budgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().hardFilters()).containsExactly("targetGender", "budgetMax");
    }

    @Test
    void winterWarmDemandUsesWinterSeasonCandidateFilter() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "秋冬保暖的",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-winter-warm", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getSeason()).isEqualTo("winter");
        assertThat(context.demandIntent().attributes()).contains("保暖");
    }

    @Test
    void candidateQueryUsesTheResolvedSeasonInsteadOfRescanningRawText() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, null, states
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null, "冬天怎么穿", null, null, null, null, null, null, null
        );
        DemandIntent effective = new DemandIntent(
                DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, request.message(),
                "OUTFIT_ADVICE", List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
                null, null, "summer", List.of(), List.of(), List.of(), null, List.of(), null,
                List.of("season"), List.of(), new BigDecimal("0.80"), List.of()
        );
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(states.apply(anyLong(), anyString(), anyString(), any(), any(), any())).thenReturn(effective);
        when(candidates.findCandidates(any())).thenReturn(List.of());

        service.buildContext(10L, "thread-resolved-season", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor =
                ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(candidates).findCandidates(captor.capture());
        assertThat(captor.getValue().getSeason()).isEqualTo("summer");
    }

    @Test
    void naturalLanguageVersatileStyleStaysOutOfExactCandidateFilter() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "平价百搭基础款",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-versatile", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getStyle()).isNull();
        assertThat(context.demandIntent().style()).contains("minimal").doesNotContain("basic");
    }

    @Test
    void synonymWarmOuterwearDemandMapsToWinterOuterwearIntent() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "有没有不容易冷的外套",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-synonym-warm", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("外套");
        assertThat(captor.getValue().getSeason()).isEqualTo("winter");
        assertThat(context.demandIntent().attributes()).contains("保暖");
    }

    @Test
    void studentDailyBudgetVisualDemandStaysSoftWithoutNumericBudget() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "大学生日常上课，别太贵，还要遮肉显腿长",
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-student-soft", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isNull();
        assertThat(context.demandIntent().budgetMax()).isNull();
        assertThat(context.demandIntent().scene()).contains("campus", "daily");
        assertThat(context.demandIntent().style()).contains("casual");
        assertThat(context.demandIntent().attributes()).contains("平价", "显瘦", "显高");
    }

    @Test
    void messageFemaleRecipientOverridesMaleProfileGender() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                mock(BehaviorSummaryService.class)
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "给女朋友买一件通勤半裙",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(userProfileService.getProfile(10L))
                .thenReturn(new UserProfileResponse(10L, "demo", null, "male", null));
        when(userProfileService.getBodyData(10L))
                .thenReturn(new UserBodyDataResponse(10L, null, null, "male", null, null, null, null, null));
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("female");
    }

    @Test
    void profileGenderIsUsedWhenMessageDoesNotMentionGender() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                mock(BehaviorSummaryService.class)
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "想要一件通勤外套",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(userProfileService.getProfile(10L))
                .thenReturn(new UserProfileResponse(10L, "demo", null, "female", null));
        when(userProfileService.getBodyData(10L))
                .thenReturn(new UserBodyDataResponse(
                        10L,
                        BigDecimal.valueOf(164),
                        BigDecimal.valueOf(52),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(recommendationCandidateQueryService).findCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("female");
    }

    @Test
    void assistantContextIncludesBehaviorSummary() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        RecommendationCandidateQueryService recommendationCandidateQueryService = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversationService = mock(ConversationApplicationService.class);
        BehaviorSummaryService behaviorSummaryService = mock(BehaviorSummaryService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                recommendationCandidateQueryService,
                conversationService,
                behaviorSummaryService
        );
        BehaviorSummaryResponse summary = new BehaviorSummaryResponse(
                List.of(1001L),
                List.of(1002L),
                List.of(1003L),
                List.of("外套"),
                List.of("commute"),
                List.of()
        );
        AssistantChatRequest request = new AssistantChatRequest(
                null,
                "想要一件通勤外套",
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
        when(behaviorSummaryService.getSummary(10L)).thenReturn(summary);
        when(conversationService.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(recommendationCandidateQueryService.findCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        var context = service.buildContext(10L, "thread-behavior", request);

        assertThat(context.behaviorSummary()).isEqualTo(summary);
    }
    @Test
    void priceQuestionCancelsPendingWithoutCallingParserOrChangingEffectiveDemand() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        DemandIntent effective = emptyIntent("\u7537\u6027");
        PendingClarification pending = new PendingClarification(
                "targetGender", "MALE", new BigDecimal("0.70"), "\u4f60\u662f\u60f3\u7b5b\u9009\u7537\u88c5\u5417\uff1f", "\u7537\u6027", "req-old");
        when(states.read(10L, "thread-pending"))
                .thenReturn(snapshot(effective, pending));
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any()))
                .thenReturn(snapshot(effective, null));
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext result = service.buildContext(10L, "thread-pending",
                new AssistantChatRequest("thread-pending", "\u4ef7\u683c\u662f\u591a\u5c11", null, null, null, null, null, null, null));

        assertThat(result.demandIntent().rawQuery()).isEqualTo(effective.rawQuery());
        assertThat(result.demandIntent().hardFilters()).isEqualTo(effective.hardFilters());
        assertThat(result.demandIntent().softPreferences()).isEqualTo(effective.softPreferences());
        verify(parser, never()).parse(any());
        verify(states).applyResolution(anyLong(), anyString(), any(), any(),
                org.mockito.ArgumentMatchers.eq("cancel_clarify"), any(), any(), any(), any());
    }

    @Test
    void parserHistoryKeepsThreeCompleteTurnsAndAtMostFourThousandCharacters() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        List<MessageResponse> history = new java.util.ArrayList<>();
        for (int index = 0; index < 5; index++) {
            history.add(new MessageResponse("user", "u".repeat(900), "done", "u" + index, LocalDateTime.now()));
            history.add(new MessageResponse("assistant", "a".repeat(900), "done", "a" + index, LocalDateTime.now()));
        }
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(history);
        when(parser.parse(any())).thenReturn(Optional.empty());
        DemandIntent effective = emptyIntent("\u7537\u6027");
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any())).thenReturn(snapshot(effective, null));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        service.buildContext(10L, "thread-history",
                new AssistantChatRequest("thread-history", "给女朋友找成熟硬朗外套", null, null, null, null, null, null, null));

        ArgumentCaptor<LlmDemandParseRequest> request = ArgumentCaptor.forClass(LlmDemandParseRequest.class);
        verify(parser).parse(request.capture());
        assertThat(request.getValue().recentHistory()).hasSize(3);
        int characters = request.getValue().recentHistory().stream()
                .mapToInt(turn -> turn.userQuery().length() + turn.assistantAnswer().length()).sum();
        assertThat(characters).isLessThanOrEqualTo(4000);
    }

    @Test
    void parserMissKeepsExistingPendingClarification() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", new BigDecimal("0.70"), "\u662f\u7ed9\u5973\u6027\u9009\u8d2d\u5417\uff1f",
                "\u7ed9\u5bf9\u8c61\u4e70\u5916\u5957", "req-old");
        DemandIntent effective = emptyIntent("\u7ed9\u5bf9\u8c61\u4e70\u5916\u5957");
        when(states.read(10L, "thread-pending-miss")).thenReturn(snapshot(effective, pending));
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.empty());
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any())).thenAnswer(invocation ->
                snapshot(effective, invocation.getArgument(7)));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext result = service.buildContext(10L, "thread-pending-miss",
                new AssistantChatRequest("thread-pending-miss", "\u518d\u770b\u770b\u522b\u7684",
                        null, null, null, null, null, null, null));

        assertThat(result.clarificationQuestion()).isEqualTo(pending.question());
        verify(states).applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), org.mockito.ArgumentMatchers.eq(pending), any());
    }

    @Test
    void rejectedParserResultKeepsExistingPendingClarification() {
        UserProfileService profiles = mock(UserProfileService.class);
        RecommendationCandidateQueryService candidates = mock(RecommendationCandidateQueryService.class);
        ConversationApplicationService conversations = mock(ConversationApplicationService.class);
        DemandIntentStateService states = mock(DemandIntentStateService.class);
        DemandIntentParseClient parser = mock(DemandIntentParseClient.class);
        AssistantContextService service = new AssistantContextService(
                profiles, candidates, conversations, mock(BehaviorSummaryService.class), states, parser);
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", new BigDecimal("0.70"), "\u662f\u7ed9\u5973\u6027\u9009\u8d2d\u5417\uff1f",
                "\u7ed9\u5bf9\u8c61\u4e70\u5916\u5957", "req-old");
        DemandIntent effective = emptyIntent("\u7ed9\u5bf9\u8c61\u4e70\u5916\u5957");
        LlmDemandParseResponse rejected = new LlmDemandParseResponse(
                "1.0", "MERGE", new LlmDemandSlots(null, null, null, null, null, null),
                Map.of(), Map.of(), false, null, null, null);
        when(states.read(10L, "thread-pending-rejected")).thenReturn(snapshot(effective, pending));
        when(conversations.getMessages(anyLong(), anyString())).thenReturn(List.of());
        when(parser.parse(any())).thenReturn(Optional.of(rejected));
        when(states.applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), any(), any())).thenAnswer(invocation ->
                snapshot(effective, invocation.getArgument(7)));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext result = service.buildContext(10L, "thread-pending-rejected",
                new AssistantChatRequest("thread-pending-rejected", "\u518d\u770b\u770b\u522b\u7684",
                        null, null, null, null, null, null, null));

        assertThat(result.clarificationQuestion()).isEqualTo(pending.question());
        verify(states).applyResolution(anyLong(), anyString(), any(), any(), anyString(),
                any(), any(), org.mockito.ArgumentMatchers.eq(pending), any());
    }

    private DemandIntentStateSnapshot snapshot(DemandIntent intent, PendingClarification pending) {
        return new DemandIntentStateSnapshot(new com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.LegacyDemandIntentAdapter()
                .adapt(intent), pending);
    }

    private IntentConstraint constraint(
            String id,
            String field,
            ConstraintOperator operator,
            String value,
            ConstraintStrength strength
    ) {
        return new IntentConstraint(
                "test-" + id, field, operator, List.of(value), strength,
                ConstraintOrigin.USER_EXPLICIT, "turn-test", null, "ACTIVE_DEMAND",
                strength == ConstraintStrength.SOFT ? BigDecimal.ONE : null);
    }
    private DemandIntent emptyIntent(String rawQuery) {
        return new DemandIntent(DemandIntent.VERSION, DemandIntent.SOURCE_JAVA_RULE, rawQuery,
                null, null, List.of(), List.of(), null, List.of(), List.of(), List.of(),
                BigDecimal.ONE, List.of());
    }

}
