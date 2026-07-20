package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.OutboxProductSearchChangeRecorder;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchSyncMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductSearchChangeRecorderTests {

    @Mock
    private ProductSearchOutboxMapper outboxMapper;

    @Test
    void recordsMinimalVersionedMessageForSpu() throws Exception {
        Instant now = Instant.parse("2026-07-20T08:00:00Z");
        ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        var recorder = new OutboxProductSearchChangeRecorder(
                outboxMapper, objectMapper, Clock.fixed(now, ZoneOffset.UTC));

        recorder.record(1002L);

        ArgumentCaptor<ProductSearchOutboxEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductSearchOutboxEvent.class);
        verify(outboxMapper).insert(eventCaptor.capture());
        ProductSearchOutboxEvent event = eventCaptor.getValue();
        ProductSearchSyncMessage message = objectMapper.readValue(
                event.getPayload(), ProductSearchSyncMessage.class);
        assertThat(event.getEventId()).isEqualTo(message.eventId());
        assertThat(event.getSpuId()).isEqualTo(1002L);
        assertThat(event.getStatus()).isEqualTo("NEW");
        assertThat(message.spuId()).isEqualTo(1002L);
        assertThat(message.eventType()).isEqualTo("PRODUCT_SEARCH_REINDEX_REQUESTED");
        assertThat(message.schemaVersion()).isEqualTo(1);
        assertThat(message.occurredAt()).isEqualTo(now);
    }
}
