package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI 任务消息拓扑和可靠性参数；重试延迟只从受控配置读取，不信任消息内任意延迟值。
 */
@Data
@ConfigurationProperties(prefix = "app.ai-task.messaging")
public class AiTaskMessagingProperties {
    private Duration retry10s = Duration.ofSeconds(10);
    private Duration retry60s = Duration.ofSeconds(60);
    private Duration retry300s = Duration.ofSeconds(300);
    private Duration confirmTimeout = Duration.ofSeconds(5);
    private Duration outboxClaimDuration = Duration.ofSeconds(30);
    private int publisherBatchSize = 20;
}
