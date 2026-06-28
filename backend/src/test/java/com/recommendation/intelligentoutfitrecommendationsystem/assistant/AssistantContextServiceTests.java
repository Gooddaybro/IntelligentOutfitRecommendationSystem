package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.AssistantChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.AssistantContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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

        service.buildContext(10L, "thread-budget", request);

        ArgumentCaptor<RecommendationCandidateQuery> captor = ArgumentCaptor.forClass(RecommendationCandidateQuery.class);
        verify(productCatalogService).findRecommendationCandidates(captor.capture());
        assertThat(captor.getValue().getBudgetMax()).isEqualTo(500);
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
}
