package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 从商品搜索 Outbox 可靠投递消息；只有发布确认成功后才把事件标记为已发布。
 */
@Component
@Profile("web")
@ConditionalOnProperty(prefix = "app.product-search-sync", name = "enabled", havingValue = "true")
public class ProductSearchOutboxRelay {
    private final RabbitTemplate rabbitTemplate;
    private final ProductSearchOutboxMapper mapper;
    private final ProductSearchSyncProperties properties;
    private final Clock clock;
    private final String relayId = "product-search-relay-" + UUID.randomUUID();

    public ProductSearchOutboxRelay(
            RabbitTemplate rabbitTemplate,
            ProductSearchOutboxMapper mapper,
            ProductSearchSyncProperties properties,
            Clock clock) {
        this.rabbitTemplate = rabbitTemplate;
        this.mapper = mapper;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 有界扫描并逐条获取租约，使多 Web 实例可以安全竞争同一批事件。
     */
    @Scheduled(fixedDelayString = "${app.product-search-sync.publisher-fixed-delay:PT1S}")
    public void publishBatch() {
        if (!properties.isPublisherEnabled()) {
            return;
        }
        LocalDateTime now = now();
        List<ProductSearchOutboxEvent> events = mapper.findPublishable(now, properties.getPublisherBatchSize());
        events.forEach(event -> publishOne(event, now));
    }

    private void publishOne(ProductSearchOutboxEvent event, LocalDateTime now) {
        if (mapper.claim(event.getEventId(), relayId, now, now.plus(properties.getClaimDuration())) != 1) {
            return;
        }
        try {
            CorrelationData data = new CorrelationData(event.getEventId());
            rabbitTemplate.convertAndSend(
                    RabbitProductSearchTopology.EXCHANGE,
                    RabbitProductSearchTopology.MAIN_ROUTING_KEY,
                    event.getPayload(),
                    data);
            CorrelationData.Confirm confirm = data.getFuture().get(
                    properties.getConfirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || data.getReturned() != null) {
                throw new IllegalStateException("rabbit publish was not confirmed");
            }
            mapper.markPublished(event.getEventId(), relayId, now());
        } catch (Exception exception) {
            mapper.releaseClaim(event.getEventId(), relayId, safeMessage(exception));
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
