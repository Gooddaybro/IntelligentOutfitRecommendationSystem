package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * 行为事件类型白名单。
 *
 * 推荐交互事件允许前端上报；交易事实事件只能由后端服务在业务流程中记录。
 */
public enum BehaviorEventType {
    FAVORITE_ADD(false),
    CART_ADD(false),
    ORDER_CREATED(false),
    PAYMENT_SUCCESS(false),
    RECOMMENDATION_EXPOSED(true),
    RECOMMENDATION_CLICKED(true),
    RECOMMENDATION_CART_ADD(true),
    RECOMMENDATION_FAVORITE_ADD(true);

    private final boolean frontendRecordable;

    BehaviorEventType(boolean frontendRecordable) {
        this.frontendRecordable = frontendRecordable;
    }

    public static BehaviorEventType parse(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            throw new BadRequestException("eventType is required");
        }
        String normalizedValue = rawValue.trim().toUpperCase(Locale.ROOT);
        try {
            return BehaviorEventType.valueOf(normalizedValue);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("eventType is not supported: " + rawValue);
        }
    }

    public static BehaviorEventType parseFrontend(String rawValue) {
        BehaviorEventType eventType = parse(rawValue);
        if (!eventType.frontendRecordable) {
            throw new BadRequestException("eventType is not allowed for frontend behavior events: " + eventType.name());
        }
        return eventType;
    }
}
