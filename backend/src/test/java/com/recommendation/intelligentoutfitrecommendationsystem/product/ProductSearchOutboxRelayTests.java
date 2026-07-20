package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxRelay;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchSyncProperties;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.RabbitProductSearchTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchOutboxRelayTests {
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ProductSearchOutboxMapper mapper = mock(ProductSearchOutboxMapper.class);
    private final ProductSearchSyncProperties properties = new ProductSearchSyncProperties();
    private ProductSearchOutboxRelay relay;

    @BeforeEach
    void setUp() {
        properties.setPublisherEnabled(true);
        properties.setConfirmTimeout(Duration.ofMillis(25));
        relay = new ProductSearchOutboxRelay(
                rabbitTemplate, mapper, properties,
                Clock.fixed(Instant.parse("2026-07-20T09:00:00Z"), ZoneOffset.UTC));
        when(mapper.findPublishable(any(), any(Integer.class))).thenReturn(List.of(event()));
        when(mapper.claim(anyString(), anyString(), any(), any())).thenReturn(1);
    }

    @Test
    void confirmedPublishMarksEventPublished() {
        completeConfirm(true);

        relay.publishBatch();

        verify(mapper).markPublished(anyString(), anyString(), any());
    }

    @Test
    void returnedMessageReleasesClaimEvenWhenBrokerAcknowledges() {
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.setReturned(new ReturnedMessage(
                    new Message(new byte[0]), 312, "NO_ROUTE",
                    RabbitProductSearchTopology.EXCHANGE, RabbitProductSearchTopology.MAIN_ROUTING_KEY));
            data.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));

        relay.publishBatch();

        verify(mapper, never()).markPublished(anyString(), anyString(), any());
        verify(mapper).releaseClaim(anyString(), anyString(), anyString());
    }

    @Test
    void disabledPublisherDoesNotScanOutbox() {
        properties.setPublisherEnabled(false);

        relay.publishBatch();

        verify(mapper, never()).findPublishable(any(), any(Integer.class));
    }

    private void completeConfirm(boolean ack) {
        doAnswer(invocation -> {
            CorrelationData data = invocation.getArgument(3);
            data.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).convertAndSend(anyString(), anyString(), anyString(), any(CorrelationData.class));
    }

    private ProductSearchOutboxEvent event() {
        ProductSearchOutboxEvent event = new ProductSearchOutboxEvent();
        event.setEventId("search-event-one");
        event.setPayload("{\"spuId\":1001}");
        return event;
    }
}
