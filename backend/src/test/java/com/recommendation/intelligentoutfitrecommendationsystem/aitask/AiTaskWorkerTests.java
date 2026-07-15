package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.rabbitmq.client.Channel;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildClientException;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskFailureClassifier;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskWorker;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.RabbitAiTaskTopology;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTaskWorkerTests {

    @Test
    void acknowledgesOnlyAfterExecutionCommits() throws Exception {
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        Channel channel = mock(Channel.class);
        when(executionService.execute(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AiTaskExecutionService.ExecutionOutcome.SUCCESS);
        AiTaskWorker worker = new AiTaskWorker(executionService);

        worker.handle(validMessage(), 7L, channel);

        verify(channel).basicAck(7L, false);
    }

    @Test
    void databaseFailureDoesNotAcknowledge() throws Exception {
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        Channel channel = mock(Channel.class);
        when(executionService.execute(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        AiTaskWorker worker = new AiTaskWorker(executionService);

        try {
            worker.handle(validMessage(), 7L, channel);
        } catch (IllegalStateException ignored) {
        }

        verify(channel, never()).basicAck(7L, false);
    }

    @Test
    void retryablePythonFailureGoesToFirstFixedRetryQueue() throws Exception {
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        when(executionService.execute(any())).thenThrow(
                new RagRebuildClientException("PYTHON_RETRYABLE", true, "temporary")
        );
        AiTaskWorker worker = new AiTaskWorker(
                executionService, rabbitTemplate, new AiTaskFailureClassifier()
        );

        worker.handle(validMessage(), 0, 7L, channel);

        verify(executionService).recordRetry(any(), eq("PYTHON_RETRYABLE"), any());
        verify(rabbitTemplate).send(
                eq(RabbitAiTaskTopology.EXCHANGE),
                eq(RabbitAiTaskTopology.RETRY_10S_ROUTING_KEY),
                any(Message.class)
        );
        verify(channel).basicAck(7L, false);
    }

    @Test
    void permanentPythonFailureGoesStraightToDlq() throws Exception {
        AiTaskExecutionService executionService = mock(AiTaskExecutionService.class);
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        Channel channel = mock(Channel.class);
        when(executionService.execute(any())).thenThrow(
                new RagRebuildClientException("PYTHON_AUTH", false, "auth failed")
        );
        AiTaskWorker worker = new AiTaskWorker(
                executionService, rabbitTemplate, new AiTaskFailureClassifier()
        );

        worker.handle(validMessage(), 0, 7L, channel);

        verify(executionService).recordFailure(any(), eq("PYTHON_AUTH"), any());
        verify(rabbitTemplate).send(
                eq(RabbitAiTaskTopology.EXCHANGE),
                eq(RabbitAiTaskTopology.DLQ_ROUTING_KEY),
                any(Message.class)
        );
        verify(channel).basicAck(7L, false);
    }

    private String validMessage() {
        return """
                {"eventId":"event-one","eventType":"ai.task.requested","schemaVersion":1,
                 "taskId":"task-one","taskType":"RAG_REBUILD","occurredAt":"2026-07-15T12:00:00Z",
                 "correlationId":"request-one","traceparent":"trace-one"}
                """;
    }
}
