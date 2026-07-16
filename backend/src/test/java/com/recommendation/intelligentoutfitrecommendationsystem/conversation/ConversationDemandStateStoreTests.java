package com.recommendation.intelligentoutfitrecommendationsystem.conversation;

import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.ConversationDemandStateSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.mapper.ConversationMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatDemandState;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.model.ChatSession;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.service.ConversationDemandStateStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationDemandStateStoreTests {

    @Test
    void staleRequestCannotReapplyOverNewerState() {
        ConversationMapper mapper = mapperWithExistingState();
        when(mapper.updateDemandState(anyLong(), anyLong(), anyString(), any(), anyString())).thenReturn(0);
        ConversationDemandStateStore store = new ConversationDemandStateStore(mapper);

        assertThatThrownBy(() -> store.transition(10L, "thread", "old-request", null, "merge", "{}", "v0",
                ignored -> new ConversationDemandStateSnapshot("stale", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("demand state changed concurrently");

        verify(mapper, never()).insertDemandTransition(anyLong(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    @Test
    void duplicateRequestReturnsWinningTransitionAfterOptimisticConflict() {
        ConversationMapper mapper = mapperWithExistingState();
        ChatDemandState replay = state("winner", "pending", 2L);
        when(mapper.updateDemandState(anyLong(), anyLong(), anyString(), any(), anyString())).thenReturn(0);
        when(mapper.findTransitionState(1L, "same-request")).thenReturn(null, replay);
        ConversationDemandStateStore store = new ConversationDemandStateStore(mapper);

        ConversationDemandStateSnapshot result = store.transition(
                10L, "thread", "same-request", null, "merge", "{}", "v0",
                ignored -> new ConversationDemandStateSnapshot("loser", null));

        assertThat(result).isEqualTo(new ConversationDemandStateSnapshot("winner", "pending"));
        verify(mapper, never()).insertDemandTransition(anyLong(), any(), anyString(), anyString(),
                anyString(), anyString(), any());
    }

    private ConversationMapper mapperWithExistingState() {
        ConversationMapper mapper = mock(ConversationMapper.class);
        ChatSession session = new ChatSession();
        session.setId(1L);
        when(mapper.findSessionByThreadIdAndUserId("thread", 10L)).thenReturn(session);
        when(mapper.findDemandState(1L)).thenReturn(state("current", null, 1L));
        return mapper;
    }

    private ChatDemandState state(String effective, String pending, long version) {
        ChatDemandState state = new ChatDemandState();
        state.setSessionId(1L);
        state.setEffectiveIntentJson(effective);
        state.setPendingClarificationJson(pending);
        state.setStateVersion(version);
        return state;
    }
}
