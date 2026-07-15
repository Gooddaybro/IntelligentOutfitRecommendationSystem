package com.recommendation.intelligentoutfitrecommendationsystem.aitask.service;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * AI 任务时间依赖配置，使事件时间和租约逻辑可以在测试中替换。
 */
@Configuration
public class AiTaskConfiguration {

    @Bean
    public Clock aiTaskClock() {
        return Clock.systemUTC();
    }
}
