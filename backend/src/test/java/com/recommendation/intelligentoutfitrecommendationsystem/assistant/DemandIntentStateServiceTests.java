package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntent;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.DemandIntentPatch;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PendingClarification;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentStateService;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DemandIntentStateServiceTests {

    @SuppressWarnings("unchecked")
    @Test
    void clarificationDoesNotEnterEffectiveIntent() {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String initial = invocation.getArgument(6);
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    return mutation.apply(new ConversationDemandStateSnapshot(initial, null));
                });
        DemandIntentStateService service = new DemandIntentStateService(store);
        DemandIntent initial = DemandIntent.empty("她适合什么衣服");
        PendingClarification pending = new PendingClarification(
                "targetGender", "FEMALE", new BigDecimal("0.70"), "是给女性选购吗？", "她适合什么衣服");

        var result = service.applyResolution(1L, "thread", "req", null,
                new DemandIntentPatch("merge", "她适合什么衣服", null, false, null,
                        List.of(), List.of(), null, List.of()), null, pending, initial);

        assertThat(result.effectiveIntent().targetGender()).isNull();
        assertThat(result.pendingClarification()).isEqualTo(pending);
    }

    @SuppressWarnings("unchecked")
    @Test
    void cancelClarificationKeepsEffectiveSnapshotAndUsesAuditAction() {
        ConversationDemandStateStore store = mock(ConversationDemandStateStore.class);
        AtomicReference<String> action = new AtomicReference<>();
        DemandIntent previous = new com.recommendation.intelligentoutfitrecommendationsystem.assistant.service.DemandIntentMerger()
                .merge(null, new DemandIntentPatch("merge", "男性外套", "male", false, "外套",
                        List.of(), List.of(), null, List.of()));
        when(store.transition(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    action.set(invocation.getArgument(4));
                    UnaryOperator<ConversationDemandStateSnapshot> mutation = invocation.getArgument(7);
                    String previousJson = new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(previous);
                    return mutation.apply(new ConversationDemandStateSnapshot(previousJson, "{}"));
                });
        DemandIntentStateService service = new DemandIntentStateService(store);

        var result = service.applyResolution(1L, "thread", "req-cancel", null,
                "cancel_clarify", null, null, null, previous);

        assertThat(action.get()).isEqualTo("cancel_clarify");
        assertThat(result.effectiveIntent()).isEqualTo(previous);
        assertThat(result.pendingClarification()).isNull();
    }
}
