package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 声明 RAG 重建主队列、三个固定延迟重试队列和最终死信队列。
 */
@Configuration
@EnableConfigurationProperties(AiTaskMessagingProperties.class)
public class RabbitAiTaskTopology {

    public static final String EXCHANGE = "ai.task.exchange.v1";
    public static final String MAIN_ROUTING_KEY = "ai.task.rag-rebuild.v1";
    public static final String MAIN_QUEUE = "ai.task.rag-rebuild.v1";
    public static final String RETRY_10S_QUEUE = "ai.task.rag-rebuild.retry-10s.v1";
    public static final String RETRY_60S_QUEUE = "ai.task.rag-rebuild.retry-60s.v1";
    public static final String RETRY_300S_QUEUE = "ai.task.rag-rebuild.retry-300s.v1";
    public static final String DLQ = "ai.task.rag-rebuild.dlq.v1";
    public static final String RETRY_10S_ROUTING_KEY = "ai.task.rag-rebuild.retry-10s.v1";
    public static final String RETRY_60S_ROUTING_KEY = "ai.task.rag-rebuild.retry-60s.v1";
    public static final String RETRY_300S_ROUTING_KEY = "ai.task.rag-rebuild.retry-300s.v1";
    public static final String DLQ_ROUTING_KEY = "ai.task.rag-rebuild.dlq.v1";

    @Bean
    public Declarables aiTaskDeclarables(AiTaskMessagingProperties properties) {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        Queue main = QueueBuilder.durable(MAIN_QUEUE).build();
        Queue retry10s = retryQueue(RETRY_10S_QUEUE, properties.getRetry10s().toMillis());
        Queue retry60s = retryQueue(RETRY_60S_QUEUE, properties.getRetry60s().toMillis());
        Queue retry300s = retryQueue(RETRY_300S_QUEUE, properties.getRetry300s().toMillis());
        Queue dlq = QueueBuilder.durable(DLQ).build();

        Binding mainBinding = BindingBuilder.bind(main).to(exchange).with(MAIN_ROUTING_KEY);
        Binding retry10sBinding = BindingBuilder.bind(retry10s).to(exchange).with(RETRY_10S_ROUTING_KEY);
        Binding retry60sBinding = BindingBuilder.bind(retry60s).to(exchange).with(RETRY_60S_ROUTING_KEY);
        Binding retry300sBinding = BindingBuilder.bind(retry300s).to(exchange).with(RETRY_300S_ROUTING_KEY);
        Binding dlqBinding = BindingBuilder.bind(dlq).to(exchange).with(DLQ_ROUTING_KEY);

        return new Declarables(
                exchange,
                main, retry10s, retry60s, retry300s, dlq,
                mainBinding, retry10sBinding, retry60sBinding, retry300sBinding, dlqBinding
        );
    }

    private Queue retryQueue(String name, long ttlMillis) {
        return QueueBuilder.durable(name)
                .withArgument("x-message-ttl", ttlMillis)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MAIN_ROUTING_KEY)
                .build();
    }
}
