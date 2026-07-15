package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.recommendation.intelligentoutfitrecommendationsystem.aitask.service.AiTaskExecutionService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Worker profile 的手动 ACK 消费入口；数据库成功提交前绝不确认消息。
 */
@Component
@Profile("worker")
public class AiTaskWorker {

    private final AiTaskExecutionService executionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiTaskWorker(AiTaskExecutionService executionService) {
        this.executionService = executionService;
    }

    @RabbitListener(
            queues = RabbitAiTaskTopology.MAIN_QUEUE,
            autoStartup = "${app.ai-task.listener-enabled:false}"
    )
    public void handle(
            String payload,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
            Channel channel
    ) throws IOException {
        AiTaskMessage message = parse(payload);
        AiTaskExecutionService.ExecutionOutcome outcome = executionService.execute(message);
        if (outcome == AiTaskExecutionService.ExecutionOutcome.LEASE_BUSY) {
            throw new IllegalStateException("AI task lease is held by another worker");
        }
        channel.basicAck(deliveryTag, false);
    }

    private AiTaskMessage parse(String payload) {
        try {
            return objectMapper.readValue(payload, AiTaskMessage.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid AI task message JSON", exception);
        }
    }
}
