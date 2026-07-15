package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildClient;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildResult;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.ConsumerInboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskMessage;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskExecutionService;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskExecutionServiceTests {

    private final AiTaskMapper taskMapper = mock(AiTaskMapper.class);
    private final ConsumerInboxMapper inboxMapper = mock(ConsumerInboxMapper.class);
    private final RagRebuildClient rebuildClient = mock(RagRebuildClient.class);
    private final TransactionOperations transactions = mock(TransactionOperations.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void successfulPythonCallCommitsTaskAndInbox() {
        AiTask task = task(AiTaskStatus.PENDING, 0L);
        when(taskMapper.findByTaskId("task-one")).thenReturn(task);
        when(taskMapper.claimTask(anyString(), anyLong(), anyString(), any(), any())).thenReturn(1);
        when(rebuildClient.rebuild("task-one", "request-one", "trace-one"))
                .thenReturn(new RagRebuildResult("task-one", "v1", 2, 4, "abc", false));
        when(taskMapper.markSuccessAndClearActiveSlot(anyString(), anyLong(), anyString())).thenReturn(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            java.util.function.Consumer<?> callback = invocation.getArgument(0);
            ((java.util.function.Consumer<Object>) callback).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
        AiTaskExecutionService service = service();

        var outcome = service.execute(message());

        assertThat(outcome).isEqualTo(AiTaskExecutionService.ExecutionOutcome.SUCCESS);
        verify(taskMapper).markSuccessAndClearActiveSlot(eq("task-one"), eq(1L), anyString());
        verify(inboxMapper).insertInbox(
                eq("rag-rebuild-worker"), eq("event-one"), eq("task-one"), any()
        );
    }

    @Test
    void alreadySuccessfulTaskSkipsPython() {
        when(taskMapper.findByTaskId("task-one")).thenReturn(task(AiTaskStatus.SUCCESS, 1L));

        var outcome = service().execute(message());

        assertThat(outcome).isEqualTo(AiTaskExecutionService.ExecutionOutcome.DUPLICATE);
        verify(rebuildClient, never()).rebuild(anyString(), anyString(), anyString());
    }

    private AiTaskExecutionService service() {
        return new AiTaskExecutionService(taskMapper, inboxMapper, rebuildClient, transactions, clock);
    }

    private AiTask task(AiTaskStatus status, long version) {
        AiTask task = new AiTask();
        task.setTaskId("task-one");
        task.setTaskType("RAG_REBUILD");
        task.setStatus(status.name());
        task.setVersion(version);
        return task;
    }

    private AiTaskMessage message() {
        return new AiTaskMessage(
                "event-one", "ai.task.requested", 1, "task-one", "RAG_REBUILD",
                "2026-07-15T12:00:00Z", "request-one", "trace-one"
        );
    }
}
