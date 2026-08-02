package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * 把商品重新投影请求写入独立 Outbox，调用方事务负责与业务修改一起提交。
 */
public class OutboxProductSearchChangeRecorder implements ProductSearchChangeRecorder {
    private final ProductSearchOutboxMapper mapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxProductSearchChangeRecorder(
            ProductSearchOutboxMapper mapper, ObjectMapper objectMapper, Clock clock) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void record(Long spuId) {
        String eventId = UUID.randomUUID().toString();
        var message = new ProductSearchSyncMessage(
                eventId, spuId, ProductSearchSyncMessage.EVENT_TYPE,
                clock.instant(), ProductSearchSyncMessage.SCHEMA_VERSION);
        ProductSearchOutboxEvent event = new ProductSearchOutboxEvent();
        event.setEventId(eventId);
        event.setSpuId(spuId);
        event.setEventType(message.eventType());
        event.setSchemaVersion(message.schemaVersion());
        event.setPayload(toJson(message));
        event.setStatus("NEW");
        event.setAvailableAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        mapper.insert(event);
    }

    private String toJson(ProductSearchSyncMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("商品搜索同步事件序列化失败", exception);
        }
    }
}
