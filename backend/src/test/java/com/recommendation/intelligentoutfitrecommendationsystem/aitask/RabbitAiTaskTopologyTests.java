package com.recommendation.intelligentoutfitrecommendationsystem.aitask;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class RabbitAiTaskTopologyTests {

    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1.8-management");

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    @Test
    void declaresMainRetryAndDeadLetterQueues() {
        List<String> queues = List.of(
                "ai.task.rag-rebuild.v1",
                "ai.task.rag-rebuild.retry-10s.v1",
                "ai.task.rag-rebuild.retry-60s.v1",
                "ai.task.rag-rebuild.retry-300s.v1",
                "ai.task.rag-rebuild.dlq.v1"
        );

        assertThat(queues)
                .allSatisfy(queue -> assertThat(rabbitAdmin.getQueueProperties(queue)).isNotNull());
    }
}
