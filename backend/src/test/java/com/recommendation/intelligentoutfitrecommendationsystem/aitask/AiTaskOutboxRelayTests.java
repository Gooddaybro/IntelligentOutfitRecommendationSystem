package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.OutboxEventMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskMessagingProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.AiTaskOutboxRelay;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.OutboxEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class AiTaskOutboxRelayTests {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
    private final AiTaskMessagingProperties properties = new AiTaskMessagingProperties();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-15T12:00:00Z"), ZoneOffset.UTC);
    private AiTaskOutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties.setConfirmTimeout(Duration.ofMillis(25));
        relay = new AiTaskOutboxRelay(rabbitTemplate, outboxMapper, properties, clock, true);
        when(outboxMapper.findPublishable(any(), any(Integer.class))).thenReturn(List.of(event()));
        when(outboxMapper.claimOutbox(anyString(), anyString(), any(), any())).thenReturn(1);
    }

    @Test
    void confirmedPublishMarksOutboxPublished() {
        completeConfirm(true);

        relay.publishBatch();

        verify(outboxMapper).markPublished(anyString(), anyString(), any());
    }

    @Test
    void negativeConfirmLeavesEventRecoverable() {
        completeConfirm(false);

        relay.publishBatch();

        verify(outboxMapper, never()).markPublished(anyString(), anyString(), any());
        verify(outboxMapper).releaseClaim(anyString(), anyString(), anyString());
    }

    @Test
    void returnedPublishLeavesEventRecoverableEvenAfterAck() {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.setReturned(new ReturnedMessage(
                    new Message(new byte[0]),
                    312,
                    "NO_ROUTE",
                    "ai.task.exchange.v1",
                    "ai.task.rag-rebuild.v1"
            ));
            correlationData.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        relay.publishBatch();

        verify(outboxMapper, never()).markPublished(anyString(), anyString(), any());
        verify(outboxMapper).releaseClaim(anyString(), anyString(), anyString());
    }

    @Test
    void confirmTimeoutLeavesEventRecoverable() {
        relay.publishBatch();

        verify(outboxMapper, never()).markPublished(anyString(), anyString(), any());
        verify(outboxMapper).releaseClaim(anyString(), anyString(), anyString());
    }

    @Test
    void brokerExceptionLeavesEventRecoverable() {
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        relay.publishBatch();

        verify(outboxMapper, never()).markPublished(anyString(), anyString(), any());
        verify(outboxMapper).releaseClaim(anyString(), anyString(), anyString());
    }

    private void completeConfirm(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));
    }

    private OutboxEvent event() {
        OutboxEvent event = new OutboxEvent();
        event.setEventId("event-one");
        event.setPayload("{\"taskId\":\"task-one\"}");
        return event;
    }
}
