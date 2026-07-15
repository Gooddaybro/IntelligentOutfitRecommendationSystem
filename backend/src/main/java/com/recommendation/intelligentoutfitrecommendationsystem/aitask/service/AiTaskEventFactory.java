package com.recommendation.intelligentoutfitrecommendationsystem.aitask.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.AiTask;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.OutboxEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 构造并序列化 v1 AI 任务事件，确保 Outbox 中只保存经过 Jackson 校验的 JSON。
 */
@Component
public class AiTaskEventFactory {

    private static final String DEFAULT_TRACEPARENT =
            "00-00000000000000000000000000000001-0000000000000001-01";

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AiTaskEventFactory(Clock clock) {
        this(new ObjectMapper(), clock);
    }

    public AiTaskEventFactory(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 创建待发布事件；事件 ID 每次调用都不同，而 taskId 在重试和 Redrive 中保持不变。
     */
    public OutboxEvent createRequested(AiTask task, String correlationId, String traceparent) {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "ai.task.requested");
        envelope.put("schemaVersion", 1);
        envelope.put("taskId", task.getTaskId());
        envelope.put("taskType", task.getTaskType());
        envelope.put("occurredAt", OffsetDateTime.now(clock).toString());
        envelope.put("correlationId", normalize(correlationId, UUID.randomUUID().toString()));
        envelope.put("traceparent", normalize(traceparent, DEFAULT_TRACEPARENT));

        OutboxEvent event = new OutboxEvent();
        event.setEventId(eventId);
        event.setAggregateType("AI_TASK");
        event.setAggregateId(task.getTaskId());
        event.setEventType("ai.task.requested");
        event.setSchemaVersion(1);
        event.setPayload(toJson(envelope));
        event.setStatus("NEW");
        event.setAvailableAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        return event;
    }

    private String toJson(Map<String, Object> envelope) {
        try {
            String payload = objectMapper.writeValueAsString(envelope);
            objectMapper.readTree(payload);
            return payload;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI task event could not be serialized", exception);
        }
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
