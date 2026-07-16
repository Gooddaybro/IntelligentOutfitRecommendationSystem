package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.client.DemandIntentParseClient;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandSlots;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.SlotEvidence;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AssistantContextServiceTests {

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
        when(states.applyResolution(anyLong(), anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new DemandIntentStateSnapshot(effective, null));
        when(candidates.findCandidates(any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-semantic", request);

        assertThat(context.demandIntent().style()).containsExactly("mature", "rugged");
        assertThat(context.clarificationQuestion()).isNull();
        verify(parser).parse(any());
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
    void demandIntentExtractsCommuteSceneStyleAndBudget() {
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
        assertThat(captor.getValue().getStyle()).isEqualTo("commute");
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
    void versatileDemandUsesExistingStyleCodeInsteadOfBasic() {
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
        assertThat(captor.getValue().getStyle()).isEqualTo("minimal");
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
}
