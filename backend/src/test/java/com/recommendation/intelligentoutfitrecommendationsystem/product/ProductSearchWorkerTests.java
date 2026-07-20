package com.recommendation.intelligentoutfitrecommendationsystem.product;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.rabbitmq.client.Channel;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.ProductSearchUnavailableException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchInboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchIncrementalProjector;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchWorker;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.RabbitProductSearchTopology;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchWorkerTests {
    private final ProductSearchIncrementalProjector projector = mock(ProductSearchIncrementalProjector.class);
    private final ProductSearchInboxMapper inboxMapper = mock(ProductSearchInboxMapper.class);
    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private ProductSearchWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ProductSearchWorker(projector, inboxMapper, rabbitTemplate,
                Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void projectsAndRecordsInboxBeforeAcknowledging() throws IOException {
        worker.handle(validPayload(), 0, 7L, channel);

        verify(projector).project(1001L);
        verify(inboxMapper).insert(eq("product-search-worker-v1"), eq("event-1"), eq(1001L), any());
        verify(channel).basicAck(7L, false);
    }

    @Test
    void duplicateEventIsAcknowledgedWithoutProjection() throws IOException {
        when(inboxMapper.exists("product-search-worker-v1", "event-1")).thenReturn(true);

        worker.handle(validPayload(), 0, 8L, channel);

        verify(projector, never()).project(anyLong());
        verify(channel).basicAck(8L, false);
    }

    @Test
    void retryableFailureUsesNextRetryQueue() throws IOException {
        doThrow(new ProductSearchUnavailableException("ES unavailable", new IOException()))
                .when(projector).project(1001L);

        worker.handle(validPayload(), 1, 9L, channel);

        verify(rabbitTemplate).send(eq(RabbitProductSearchTopology.EXCHANGE),
                eq(RabbitProductSearchTopology.RETRY_60S_ROUTING_KEY), any(Message.class));
        verify(channel).basicAck(9L, false);
    }

    @Test
    void invalidMessageGoesDirectlyToDeadLetterQueue() throws IOException {
        worker.handle("{}", 0, 10L, channel);

        verify(rabbitTemplate).send(eq(RabbitProductSearchTopology.EXCHANGE),
                eq(RabbitProductSearchTopology.DLQ_ROUTING_KEY), any(Message.class));
        verify(projector, never()).project(anyLong());
        verify(channel).basicAck(10L, false);
    }

    @Test
    void permanentElasticsearchFailureGoesDirectlyToDeadLetterQueue() throws IOException {
        ElasticsearchException failure = mock(ElasticsearchException.class);
        when(failure.status()).thenReturn(400);
        doThrow(failure).when(projector).project(1001L);

        worker.handle(validPayload(), 0, 11L, channel);

        verify(rabbitTemplate).send(eq(RabbitProductSearchTopology.EXCHANGE),
                eq(RabbitProductSearchTopology.DLQ_ROUTING_KEY), any(Message.class));
        verify(channel).basicAck(11L, false);
    }

    private String validPayload() {
        return """
                {"eventId":"event-1","spuId":1001,
                 "eventType":"PRODUCT_SEARCH_REINDEX_REQUESTED",
                 "occurredAt":"2026-07-20T10:00:00Z","schemaVersion":1}
                """;
    }
}
