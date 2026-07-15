package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper.OutboxEventMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.OutboxEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Web 进程的 Outbox Relay；只有 correlated Publisher Confirm ACK 后才提交 PUBLISHED。
 */
@Component
@Profile("web")
public class AiTaskOutboxRelay {

    private final RabbitTemplate rabbitTemplate;
    private final OutboxEventMapper outboxMapper;
    private final AiTaskMessagingProperties properties;
    private final Clock clock;
    private final boolean publisherEnabled;
    private final ApplicationMetrics metrics;
    private final String relayId = "relay-" + UUID.randomUUID();

    public AiTaskOutboxRelay(
            RabbitTemplate rabbitTemplate,
            OutboxEventMapper outboxMapper,
            AiTaskMessagingProperties properties,
            Clock clock,
            @Value("${app.ai-task.publisher-enabled:false}") boolean publisherEnabled
    ) {
        this(rabbitTemplate, outboxMapper, properties, clock, publisherEnabled, null);
    }

    @Autowired
    public AiTaskOutboxRelay(
            RabbitTemplate rabbitTemplate,
            OutboxEventMapper outboxMapper,
            AiTaskMessagingProperties properties,
            Clock clock,
            @Value("${app.ai-task.publisher-enabled:false}") boolean publisherEnabled,
            ApplicationMetrics metrics
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxMapper = outboxMapper;
        this.properties = properties;
        this.clock = clock;
        this.publisherEnabled = publisherEnabled;
        this.metrics = metrics;
    }

    /**
     * 有界扫描并逐条发布，单条失败只释放自己的租约，供下一轮恢复。
     */
    @Scheduled(fixedDelayString = "${app.ai-task.publisher-fixed-delay-ms:1000}")
    public void publishBatch() {
        if (!publisherEnabled) {
            return;
        }

        LocalDateTime now = now();
        List<OutboxEvent> events = outboxMapper.findPublishable(now, properties.getPublisherBatchSize());
        for (OutboxEvent event : events) {
            publishOne(event, now);
        }
    }

    private void publishOne(OutboxEvent event, LocalDateTime now) {
        LocalDateTime claimUntil = now.plus(properties.getOutboxClaimDuration());
        if (outboxMapper.claimOutbox(event.getEventId(), relayId, now, claimUntil) != 1) {
            return;
        }

        try {
            CorrelationData correlationData = new CorrelationData(event.getEventId());
            rabbitTemplate.convertAndSend(
                    RabbitAiTaskTopology.EXCHANGE,
                    RabbitAiTaskTopology.MAIN_ROUTING_KEY,
                    event.getPayload(),
                    correlationData
            );
            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getConfirmTimeout().toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!confirm.ack() || correlationData.getReturned() != null) {
                throw new IllegalStateException("rabbit publish was not confirmed");
            }
            outboxMapper.markPublished(event.getEventId(), relayId, now());
            recordPublish("confirmed");
        } catch (Exception exception) {
            outboxMapper.releaseClaim(event.getEventId(), relayId, safeMessage(exception));
            recordPublish("error");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String safeMessage(Exception exception) {
        String value = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void recordPublish(String outcome) {
        if (metrics != null) {
            metrics.recordAiTaskPublish(outcome);
        }
    }
}
