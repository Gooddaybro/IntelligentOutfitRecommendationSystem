package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 未支付订单超时关闭配置。
 *
 * 该配置把 MVP 阶段的轮询间隔、超时时长和批量大小留在配置文件中，避免把交易生命周期
 * 固化在调度代码里，后续接入 MQ 或分布式任务时也能复用同一业务参数。
 */
@ConfigurationProperties(prefix = "order")
public class OrderTimeoutProperties {

    private int unpaidTimeoutMinutes = 30;

    private int timeoutCloseBatchSize = 50;

    private long timeoutCloseFixedDelayMs = 60000;

    public int getUnpaidTimeoutMinutes() {
        return unpaidTimeoutMinutes;
    }

    public void setUnpaidTimeoutMinutes(int unpaidTimeoutMinutes) {
        this.unpaidTimeoutMinutes = unpaidTimeoutMinutes;
    }

    public int getTimeoutCloseBatchSize() {
        return timeoutCloseBatchSize;
    }

    public void setTimeoutCloseBatchSize(int timeoutCloseBatchSize) {
        this.timeoutCloseBatchSize = timeoutCloseBatchSize;
    }

    public long getTimeoutCloseFixedDelayMs() {
        return timeoutCloseFixedDelayMs;
    }

    public void setTimeoutCloseFixedDelayMs(long timeoutCloseFixedDelayMs) {
        this.timeoutCloseFixedDelayMs = timeoutCloseFixedDelayMs;
    }
}
