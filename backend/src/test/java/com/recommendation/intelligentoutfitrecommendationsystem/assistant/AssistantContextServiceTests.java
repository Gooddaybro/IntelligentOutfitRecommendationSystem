package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantContext;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AssistantContextServiceTests {

    @Test
    void extractsBudgetMaxFromNaturalLanguageWhenRequestFilterIsMissing() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
                conversationService
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-budget", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().budgetMax()).isEqualTo(500);
        assertThat(context.demandIntent().hardFilters()).contains("budgetMax");
    }

    @Test
    void explicitBudgetMaxWinsOverMessageBudget() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
                conversationService
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-budget", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(300);
    }

    @Test
    void messageMaleDemandSetsRecommendationGenderToMale() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
                conversationService
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("male");
        assertThat(context.demandIntent().targetGender()).isEqualTo("male");
        assertThat(context.demandIntent().hardFilters()).containsExactly("targetGender");
    }

    @Test
    void messageSkirtDemandSetsRecommendationCategoryToSkirtCategory() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-category", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getCategory()).isEqualTo("半裙");
        assertThat(context.demandIntent().targetGender()).isEqualTo("female");
        assertThat(context.demandIntent().category()).isEqualTo("半裙");
        assertThat(context.demandIntent().hardFilters()).containsExactly("targetGender", "category");
    }

    @Test
    void demandIntentExtractsCommuteSceneStyleAndBudget() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        AssistantContext context = service.buildContext(10L, "thread-intent", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
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
    void messageFemaleRecipientOverridesMaleProfileGender() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
                conversationService
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("female");
    }

    @Test
    void profileGenderIsUsedWhenMessageDoesNotMentionGender() {
        UserProfileService userProfileService = mock(UserProfileService.class);
        ProductCatalogService productCatalogService = mock(ProductCatalogService.class);
        ConversationService conversationService = mock(ConversationService.class);
        AssistantContextService service = new AssistantContextService(
                userProfileService,
                productCatalogService,
                conversationService
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
        when(productCatalogService.findRecommendationCandidates(org.mockito.Mockito.any())).thenReturn(List.of());

        service.buildContext(10L, "thread-gender", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getGender()).isEqualTo("female");
    }
}
