package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskExecutionService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Worker profile 的手动 ACK 消费入口；数据库成功提交前绝不确认消息。
 */
@Component
@Profile("worker")
public class AiTaskWorker {

    private final AiTaskExecutionService executionService;
    private final RabbitTemplate rabbitTemplate;
    private final AiTaskFailureClassifier failureClassifier;
    private final ApplicationMetrics metrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiTaskWorker(AiTaskExecutionService executionService) {
        this(executionService, null, new AiTaskFailureClassifier(), null);
    }

    public AiTaskWorker(
            AiTaskExecutionService executionService,
            RabbitTemplate rabbitTemplate,
            AiTaskFailureClassifier failureClassifier
    ) {
        this(executionService, rabbitTemplate, failureClassifier, null);
    }

    @Autowired
    public AiTaskWorker(
            AiTaskExecutionService executionService,
            RabbitTemplate rabbitTemplate,
            AiTaskFailureClassifier failureClassifier,
            ApplicationMetrics metrics
    ) {
        this.executionService = executionService;
        this.rabbitTemplate = rabbitTemplate;
        this.failureClassifier = failureClassifier;
        this.metrics = metrics;
    }

    @RabbitListener(
            queues = RabbitAiTaskTopology.MAIN_QUEUE,
            autoStartup = "${app.ai-task.listener-enabled:false}"
    )
    public void listen(Message rabbitMessage, Channel channel) throws IOException {
        int retryStage = retryStage(rabbitMessage.getMessageProperties());
        String payload = new String(rabbitMessage.getBody(), StandardCharsets.UTF_8);
        handle(payload, retryStage, rabbitMessage.getMessageProperties().getDeliveryTag(), channel);
    }

    public void handle(String payload, long deliveryTag, Channel channel) throws IOException {
        handle(payload, 0, deliveryTag, channel);
    }

    /**
     * 执行并按固定阶段路由失败；状态更新或重新发布失败时保留原消息未 ACK。
     */
    public void handle(String payload, int retryStage, long deliveryTag, Channel channel) throws IOException {
        long startedAt = System.nanoTime();
        AiTaskMessage message = null;
        try {
            message = parse(payload);
            AiTaskExecutionService.ExecutionOutcome outcome = executionService.execute(message);
            if (outcome == AiTaskExecutionService.ExecutionOutcome.LEASE_BUSY) {
                throw new IllegalStateException("AI task lease is held by another worker");
            }
            channel.basicAck(deliveryTag, false);
            recordConsume(
                    outcome == AiTaskExecutionService.ExecutionOutcome.DUPLICATE
                            ? "duplicate" : "success",
                    startedAt
            );
        } catch (RuntimeException exception) {
            handleFailure(payload, message, retryStage, deliveryTag, channel, exception, startedAt);
        }
    }

    private AiTaskMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, AiTaskMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid AI task message JSON", exception);
        }
    }

    private void handleFailure(
            String payload,
            AiTaskMessage message,
            int retryStage,
            long deliveryTag,
            Channel channel,
            RuntimeException exception,
            long startedAt
    ) throws IOException {
        AiTaskFailureClassifier.Decision decision = failureClassifier.classify(exception, retryStage);
        if (decision.action() == AiTaskFailureClassifier.Action.NO_ACK) {
            recordConsume("error", startedAt);
            throw exception;
        }
        if (rabbitTemplate == null) {
            throw exception;
        }

        if (decision.action() == AiTaskFailureClassifier.Action.RETRY) {
            executionService.recordRetry(message, decision.code(), decision.summary());
            publish(payload, retryRoutingKey(decision.nextRetryStage()), decision.nextRetryStage());
            if (metrics != null) {
                metrics.recordAiTaskRetry(Integer.toString(decision.nextRetryStage()));
            }
            recordConsume("retry", startedAt);
        } else {
            if (message != null) {
                executionService.recordFailure(message, decision.code(), decision.summary());
            }
            publish(payload, RabbitAiTaskTopology.DLQ_ROUTING_KEY, retryStage);
            recordConsume("dlq", startedAt);
        }
        channel.basicAck(deliveryTag, false);
    }

    private void publish(String payload, String routingKey, int retryStage) {
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        messageProperties.setHeader("x-retry-stage", retryStage);
        rabbitTemplate.send(
                RabbitAiTaskTopology.EXCHANGE,
                routingKey,
                new Message(payload.getBytes(StandardCharsets.UTF_8), messageProperties)
        );
    }

    private String retryRoutingKey(int retryStage) {
        return switch (retryStage) {
            case 1 -> RabbitAiTaskTopology.RETRY_10S_ROUTING_KEY;
            case 2 -> RabbitAiTaskTopology.RETRY_60S_ROUTING_KEY;
            case 3 -> RabbitAiTaskTopology.RETRY_300S_ROUTING_KEY;
            default -> throw new IllegalArgumentException("unsupported retry stage");
        };
    }

    private int retryStage(MessageProperties properties) {
        Object value = properties.getHeaders().get("x-retry-stage");
        if (value instanceof Number number) {
            int stage = number.intValue();
            return stage >= 0 && stage <= 3 ? stage : 0;
        }
        return 0;
    }

    private void recordConsume(String outcome, long startedAt) {
        if (metrics != null) {
            metrics.recordAiTaskConsume(
                    outcome,
                    Duration.ofNanos(System.nanoTime() - startedAt)
            );
        }
    }
}
