package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 商品搜索专用 Worker：幂等消费、读取 MySQL 当前事实并投影到 Elasticsearch。
 */
@Component
@Profile("worker")
@ConditionalOnExpression("${app.product-search-sync.enabled:false} and ${app.elasticsearch.enabled:false}")
public class ProductSearchWorker {
    private final ProductSearchIncrementalProjector projector;
    private final ProductSearchInboxMapper inboxMapper;
    private final ProductSearchConsumptionRecorder consumptionRecorder;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    public ProductSearchWorker(
            ProductSearchIncrementalProjector projector,
            ProductSearchInboxMapper inboxMapper,
            ProductSearchConsumptionRecorder consumptionRecorder,
            RabbitTemplate rabbitTemplate) {
        this.projector = projector;
        this.inboxMapper = inboxMapper;
        this.consumptionRecorder = consumptionRecorder;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(
            queues = RabbitProductSearchTopology.MAIN_QUEUE,
            autoStartup = "${app.product-search-sync.listener-enabled:false}")
    public void listen(Message rabbitMessage, Channel channel) throws IOException {
        String payload = new String(rabbitMessage.getBody(), StandardCharsets.UTF_8);
        handle(payload, retryStage(rabbitMessage.getMessageProperties()),
                rabbitMessage.getMessageProperties().getDeliveryTag(), channel);
    }

    public void handle(String payload, int retryStage, long deliveryTag, Channel channel) throws IOException {
        ProductSearchSyncMessage message;
        try {
            message = parseAndValidate(payload);
        } catch (IllegalArgumentException exception) {
            publish(payload, RabbitProductSearchTopology.DLQ_ROUTING_KEY, retryStage);
            channel.basicAck(deliveryTag, false);
            return;
        }

        if (inboxMapper.exists(ProductSearchConsumptionRecorder.CONSUMER_NAME, message.eventId())) {
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            projector.project(message.spuId());
            consumptionRecorder.record(message);
            channel.basicAck(deliveryTag, false);
        } catch (DuplicateKeyException duplicate) {
            // Inbox 冲突会使记录事务回滚，因此不会误推进缓存版本。
            channel.basicAck(deliveryTag, false);
        } catch (RuntimeException exception) {
            routeFailure(payload, retryStage, exception);
            channel.basicAck(deliveryTag, false);
        }
    }

    private ProductSearchSyncMessage parseAndValidate(String payload) {
        try {
            ProductSearchSyncMessage message = objectMapper.readValue(payload, ProductSearchSyncMessage.class);
            if (message.eventId() == null || message.eventId().isBlank()
                    || message.spuId() == null || message.spuId() <= 0
                    || !ProductSearchSyncMessage.EVENT_TYPE.equals(message.eventType())
                    || message.schemaVersion() != ProductSearchSyncMessage.SCHEMA_VERSION
                    || message.occurredAt() == null) {
                throw new IllegalArgumentException("invalid product search sync message");
            }
            return message;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid product search sync message JSON", exception);
        }
    }

    private void routeFailure(String payload, int retryStage, RuntimeException failure) {
        if (isPermanent(failure) || retryStage >= 3) {
            publish(payload, RabbitProductSearchTopology.DLQ_ROUTING_KEY, retryStage);
            return;
        }
        int nextStage = retryStage + 1;
        publish(payload, retryRoutingKey(nextStage), nextStage);
    }

    private boolean isPermanent(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ElasticsearchException elasticsearchException) {
                int status = elasticsearchException.status();
                return status >= 400 && status < 500 && status != 408 && status != 429;
            }
        }
        return false;
    }

    private void publish(String payload, String routingKey, int retryStage) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setHeader("x-retry-stage", retryStage);
        rabbitTemplate.send(RabbitProductSearchTopology.EXCHANGE, routingKey,
                new Message(payload.getBytes(StandardCharsets.UTF_8), properties));
    }

    private String retryRoutingKey(int retryStage) {
        return switch (retryStage) {
            case 1 -> RabbitProductSearchTopology.RETRY_10S_ROUTING_KEY;
            case 2 -> RabbitProductSearchTopology.RETRY_60S_ROUTING_KEY;
            case 3 -> RabbitProductSearchTopology.RETRY_300S_ROUTING_KEY;
            default -> throw new IllegalArgumentException("unsupported retry stage");
        };
    }

    private int retryStage(MessageProperties properties) {
        Object value = properties.getHeaders().get("x-retry-stage");
        if (value instanceof Number number && number.intValue() >= 0 && number.intValue() <= 3) {
            return number.intValue();
        }
        return 0;
    }

}
