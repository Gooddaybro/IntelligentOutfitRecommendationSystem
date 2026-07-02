package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 行为事件来源，用于区分交易链路、推荐交互和系统补偿写入。
 */
public enum BehaviorEventSource {
    COMMERCE,
    ASSISTANT_RECOMMENDATION,
    SYSTEM;

    public static BehaviorEventSource parseOrDefault(String rawValue, BehaviorEventSource defaultSource) {
        if (!StringUtils.hasText(rawValue)) {
            return defaultSource;
        }
        String normalizedValue = rawValue.trim().toUpperCase(Locale.ROOT);
        try {
            return BehaviorEventSource.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("source is not supported: " + rawValue);
        }
    }
}
