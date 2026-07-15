package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.dto.CreateAiTaskRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.AiTaskRedriveAuditMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.OutboxEventMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskApplicationService;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskEventFactory;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskStatus;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskType;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiTaskApplicationServiceTests {

    @Test
    void duplicateActiveSlotRaceReturnsTheTaskInsertedByTheWinner() {
        AiTaskMapper taskMapper = mock(AiTaskMapper.class);
        OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
        AiTaskRedriveAuditMapper auditMapper = mock(AiTaskRedriveAuditMapper.class);
        AiTask winner = new AiTask();
        winner.setTaskId("winner-task");
        winner.setTaskType(AiTaskType.RAG_REBUILD.name());
        winner.setStatus(AiTaskStatus.PENDING.name());
        when(taskMapper.findActiveTask(AiTaskType.RAG_REBUILD.name()))
                .thenReturn(null, winner);
        when(taskMapper.insertTask(any())).thenThrow(new DuplicateKeyException("active slot"));
        AiTaskApplicationService service = new AiTaskApplicationService(
                taskMapper,
                outboxMapper,
                auditMapper,
                new AiTaskEventFactory(new ObjectMapper(), Clock.systemUTC())
        );

        var response = service.createTask(
                1L,
                new CreateAiTaskRequest(AiTaskType.RAG_REBUILD),
                "request-one",
                null
        );

        assertThat(response.taskId()).isEqualTo("winner-task");
        assertThat(response.replayed()).isTrue();
    }
}
