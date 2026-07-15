package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging.RabbitAiTaskTopology;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class AiTaskRetryDlqIntegrationTests {

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1.8-management");

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Test
    void fixedRetryQueuesDeadLetterBackToMainAndFinalRouteReachesDlq() {
        List<String> retryRoutes = List.of(
                RabbitAiTaskTopology.RETRY_10S_ROUTING_KEY,
                RabbitAiTaskTopology.RETRY_60S_ROUTING_KEY,
                RabbitAiTaskTopology.RETRY_300S_ROUTING_KEY
        );
        for (int index = 0; index < retryRoutes.size(); index++) {
            int stage = index + 1;
            rabbitTemplate.send(
                    RabbitAiTaskTopology.EXCHANGE,
                    retryRoutes.get(index),
                    message("retry-" + stage, stage)
            );

            Message redelivered = rabbitTemplate.receive(RabbitAiTaskTopology.MAIN_QUEUE, 3000);
            assertThat(redelivered).isNotNull();
            Object actualStage = redelivered.getMessageProperties().getHeader("x-retry-stage");
            assertThat(actualStage).isEqualTo(stage);
        }

        rabbitTemplate.send(
                RabbitAiTaskTopology.EXCHANGE,
                RabbitAiTaskTopology.DLQ_ROUTING_KEY,
                message("failed", 3)
        );
        Message failed = rabbitTemplate.receive(RabbitAiTaskTopology.DLQ, 3000);
        assertThat(failed).isNotNull();
        assertThat(new String(failed.getBody(), StandardCharsets.UTF_8)).isEqualTo("failed");
    }

    private Message message(String body, int retryStage) {
        MessageProperties properties = new MessageProperties();
        properties.setHeader("x-retry-stage", retryStage);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }
}
