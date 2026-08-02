package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitProductSearchTopologyTests {

    @Test
    void declaresIndependentMainRetryAndDeadLetterQueues() {
        ProductSearchSyncProperties properties = new ProductSearchSyncProperties();
        properties.setRetry10s(Duration.ofSeconds(10));
        properties.setRetry60s(Duration.ofSeconds(60));
        properties.setRetry300s(Duration.ofSeconds(300));

        Declarables declarables = new RabbitProductSearchTopology().productSearchDeclarables(properties);

        assertThat(declarables.getDeclarablesByType(Queue.class))
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        RabbitProductSearchTopology.MAIN_QUEUE,
                        RabbitProductSearchTopology.RETRY_10S_QUEUE,
                        RabbitProductSearchTopology.RETRY_60S_QUEUE,
                        RabbitProductSearchTopology.RETRY_300S_QUEUE,
                        RabbitProductSearchTopology.DLQ);
        Queue retry10s = declarables.getDeclarablesByType(Queue.class).stream()
                .filter(queue -> RabbitProductSearchTopology.RETRY_10S_QUEUE.equals(queue.getName()))
                .findFirst().orElseThrow();
        assertThat(retry10s.getArguments())
                .containsEntry("x-message-ttl", 10_000L)
                .containsEntry("x-dead-letter-exchange", RabbitProductSearchTopology.EXCHANGE)
                .containsEntry("x-dead-letter-routing-key", RabbitProductSearchTopology.MAIN_ROUTING_KEY);
    }
}
