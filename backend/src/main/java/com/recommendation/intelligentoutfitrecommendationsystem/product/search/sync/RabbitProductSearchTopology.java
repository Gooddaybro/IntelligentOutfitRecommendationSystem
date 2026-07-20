package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 商品搜索同步使用独立的交换机、队列和死信链路，避免与 AI 异步任务互相影响。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.product-search-sync", name = "enabled", havingValue = "true")
public class RabbitProductSearchTopology {
    public static final String EXCHANGE = "product.search.exchange.v1";
    public static final String MAIN_QUEUE = "product.search.reindex.v1";
    public static final String MAIN_ROUTING_KEY = "product.search.reindex.v1";
    public static final String RETRY_10S_QUEUE = "product.search.reindex.retry-10s.v1";
    public static final String RETRY_60S_QUEUE = "product.search.reindex.retry-60s.v1";
    public static final String RETRY_300S_QUEUE = "product.search.reindex.retry-300s.v1";
    public static final String DLQ = "product.search.reindex.dlq.v1";
    public static final String RETRY_10S_ROUTING_KEY = RETRY_10S_QUEUE;
    public static final String RETRY_60S_ROUTING_KEY = RETRY_60S_QUEUE;
    public static final String RETRY_300S_ROUTING_KEY = RETRY_300S_QUEUE;
    public static final String DLQ_ROUTING_KEY = DLQ;

    @Bean
    Declarables productSearchDeclarables(ProductSearchSyncProperties properties) {
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
        return new Declarables(exchange, main, retry10s, retry60s, retry300s, dlq,
                mainBinding, retry10sBinding, retry60sBinding, retry300sBinding, dlqBinding);
    }

    private Queue retryQueue(String name, long ttlMillis) {
        return QueueBuilder.durable(name)
                .withArgument("x-message-ttl", ttlMillis)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", MAIN_ROUTING_KEY)
                .build();
    }
}
