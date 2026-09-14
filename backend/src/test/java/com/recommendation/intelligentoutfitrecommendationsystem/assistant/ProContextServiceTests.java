package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.ProChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProContextService;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.ProRunRegistry;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorSummaryService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.service.UserProfileService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProContextServiceTests {
    private final UserProfileService profiles = mock(UserProfileService.class);
    private final ConversationApplicationService conversations = mock(ConversationApplicationService.class);
    private final BehaviorSummaryService behaviors = mock(BehaviorSummaryService.class);
    private final ProContextService service = new ProContextService(profiles, conversations, behaviors);
    private final ProRunRegistry.RunCredentials credentials = new ProRunRegistry.RunCredentials("run", "secret");

    @Test
    void onlyDependsOnTrustedReadServicesRatherThanLiteDemandOrCandidateServices() {
        assertThat(Arrays.stream(ProContextService.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(java.lang.reflect.Field::getType))
                .containsExactlyInAnyOrder(UserProfileService.class, ConversationApplicationService.class,
                        BehaviorSummaryService.class);
    }

    @Test
    void assemblesTrustedFactsKeepingExplicitFiltersSeparateFromPreferences() {
        when(profiles.getProfile(7L)).thenReturn(new UserProfileResponse(7L, "name", null, "female", null));
        when(profiles.getBodyData(7L)).thenReturn(new UserBodyDataResponse(7L, new BigDecimal("175"),
                new BigDecimal("70"), "male", null, null, null, null, "loose"));
        when(profiles.getPreferences(7L)).thenReturn(new UserPreferencesResponse(7L, List.of("casual"),
                List.of("blue"), List.of("red"), List.of("shirts"), new BigDecimal("50"), new BigDecimal("800")));
        when(behaviors.getSummary(7L)).thenReturn(new BehaviorSummaryResponse(List.of(11L), List.of(12L),
                List.of(13L), List.of("pants"), List.of("sport"), List.of(14L)));
        var request = new ProChatRequest("untrusted-thread", "帮我选衣服", "pro", " jacket ", "commute",
                "winter", "cotton", "regular", "female", new BigDecimal("299.90"));

        var result = service.build(7L, "owned-thread", request, "req", credentials);

        assertThat(result.contractVersion()).isEqualTo("assistant-v2");
        assertThat(result.agentMode()).isEqualTo("pro");
        assertThat(result.threadId()).isEqualTo("owned-thread");
        assertThat(result.requestId()).isEqualTo("req");
        assertThat(result.runId()).isEqualTo("run");
        assertThat(result.toolRunToken()).isEqualTo("secret");
        assertThat(result.query()).isEqualTo(request.message());
        assertThat(result.userContext()).containsEntry("user_id", 7L).containsEntry("gender", "male")
                .containsEntry("height_cm", new BigDecimal("175")).containsEntry("weight_kg", new BigDecimal("70"))
                .containsEntry("preferred_fit", "loose").containsEntry("preferred_styles", List.of("casual"))
                .containsEntry("budget_max", new BigDecimal("800"))
                .containsEntry("recent_interest_spu_ids", List.of(11L));
        assertThat(result.explicitFilters()).containsEntry("category", "jacket").containsEntry("gender", "female")
                .containsEntry("budget_max", "299.90").hasSize(7);
        assertThat(result.currentProductRefs()).isEmpty();
        verify(conversations).getMessages(7L, "owned-thread");
    }

    @Test
    void fallsBackToProfileGenderAndDoesNotInferFiltersFromMessageOrPreferences() {
        when(profiles.getProfile(7L)).thenReturn(new UserProfileResponse(7L, null, null, "female", null));
        var result = service.build(7L, "thread", request(), "req", credentials);
        assertThat(result.userContext()).containsEntry("gender", "female");
        assertThat(result.explicitFilters()).isEmpty();
        assertThat(result.chatHistory()).isEmpty();
    }

    @Test
    void retainsOnlyLatestHundredCompletedPairsWithBoundedText() {
        List<MessageResponse> messages = new ArrayList<>();
        messages.add(message("assistant", "orphan", "succeeded"));
        for (int index = 0; index < 102; index++) {
            messages.add(message("user", "q" + index + "x".repeat(2100), "succeeded"));
            messages.add(message("assistant", "a".repeat(8100), "succeeded"));
        }
        messages.add(message("user", "failed question", "succeeded"));
        messages.add(message("assistant", "partial", "failed"));
        messages.add(message("user", "current question", "succeeded"));
        when(conversations.getMessages(7L, "thread")).thenReturn(messages);
        var result = service.build(7L, "thread", request(), "req", credentials);
        assertThat(result.chatHistory()).hasSize(100);
        assertThat(result.chatHistory().getFirst().userQuery()).startsWith("q2").hasSize(2000);
        assertThat(result.chatHistory().getLast().userQuery()).startsWith("q101");
        assertThat(result.chatHistory()).allSatisfy(turn -> assertThat(turn.assistantAnswer()).hasSize(8000));
    }

    private ProChatRequest request() {
        return new ProChatRequest(null, "给妈妈买女装，预算300", "pro", null, " ", null, null, null, null, null);
    }

    @Test
    void doesNotPairLateAnswerWithAnotherRequestOrIncludeCurrentTurn() {
        when(conversations.getMessages(7L, "thread")).thenReturn(List.of(
                new MessageResponse("user", "question A", "succeeded", "a", null),
                new MessageResponse("user", "question B", "succeeded", "b", null),
                new MessageResponse("assistant", "answer A", "succeeded", "a", null),
                new MessageResponse("user", "current", "succeeded", "req", null),
                new MessageResponse("assistant", "unexpected current answer", "succeeded", "req", null)));
        assertThat(service.build(7L, "thread", request(), "req", credentials).chatHistory()).isEmpty();
    }

    private MessageResponse message(String role, String content, String status) {
        return new MessageResponse(role, content, status, "old", null);
    }
}
