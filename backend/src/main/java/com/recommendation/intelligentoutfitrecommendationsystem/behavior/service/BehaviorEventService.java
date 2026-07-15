package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorEventResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.BehaviorMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper.RecommendationAttributionMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 行为事件写入服务。
 *
 * 公开推荐交互写入保持严格校验；内部交易事件写入采用 best-effort，
 * 避免反馈闭环埋点故障影响购物车、下单、支付等主流程。
 */
@Service
public class BehaviorEventService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BehaviorEventService.class);
    private static final int MAX_IDENTIFIER_LENGTH = 64;

    private final BehaviorMapper behaviorMapper;
    private final RecommendationAttributionMapper recommendationAttributionMapper;
    private final ApplicationMetrics applicationMetrics;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BehaviorEventService(
            BehaviorMapper behaviorMapper,
            RecommendationAttributionMapper recommendationAttributionMapper,
            ApplicationMetrics applicationMetrics
    ) {
        this.behaviorMapper = behaviorMapper;
        this.recommendationAttributionMapper = recommendationAttributionMapper;
        this.applicationMetrics = applicationMetrics;
    }

    @Transactional
    public BehaviorEventResponse recordRecommendationInteraction(Long userId, BehaviorEventRequest request) {
        validateUserId(userId);
        if (request == null) {
            throw new BadRequestException("request body is required");
        }

        BehaviorEventType eventType = BehaviorEventType.parseFrontend(request.eventType());
        BehaviorEventSource source = BehaviorEventSource.parseOrDefault(
                request.source(),
                BehaviorEventSource.ASSISTANT_RECOMMENDATION
        );
        if (source != BehaviorEventSource.ASSISTANT_RECOMMENDATION) {
            throw new BadRequestException("source is not allowed for frontend behavior events: " + source.name());
        }

        String eventId = normalizeEventId(request.eventId());
        String recommendationId = normalizeOptionalIdentifier(request.recommendationId(), "recommendationId");
        validateOwnedSelectedItem(userId, request.spuId(), request.skuId(), recommendationId);
        int insertedRows = behaviorMapper.insert(toEvent(
                eventId,
                userId,
                eventType,
                source,
                request.spuId(),
                request.skuId(),
                request.threadId(),
                request.requestId(),
                request.orderNo(),
                request.quantity(),
                request.metadata(),
                recommendationId
        ));
        recordFunnel(eventType, recommendationId, insertedRows);
        return new BehaviorEventResponse(eventId);
    }

    public void recordBusinessEvent(BehaviorEventCommand command) {
        try {
            if (command == null) {
                throw new BadRequestException("behavior event command is required");
            }
            validateUserId(command.userId());
            BehaviorEventType eventType = BehaviorEventType.parse(command.eventType());
            BehaviorEventSource source = BehaviorEventSource.parseOrDefault(
                    command.source(),
                    BehaviorEventSource.COMMERCE
            );
            String recommendationId = resolveBusinessRecommendation(command, eventType);
            int insertedRows = behaviorMapper.insert(toEvent(
                    normalizeEventId(command.eventId()),
                    command.userId(),
                    eventType,
                    source,
                    command.spuId(),
                    command.skuId(),
                    command.threadId(),
                    command.requestId(),
                    command.orderNo(),
                    command.quantity(),
                    command.metadata(),
                    recommendationId
            ));
            recordFunnel(eventType, recommendationId, insertedRows);
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to record behavior event from business flow.", exception);
        }
    }

    private BehaviorEvent toEvent(
            String eventId,
            Long userId,
            BehaviorEventType eventType,
            BehaviorEventSource source,
            Long spuId,
            Long skuId,
            String threadId,
            String requestId,
            String orderNo,
            Integer quantity,
            Map<String, Object> metadata,
            String recommendationId
    ) {
        BehaviorEvent event = new BehaviorEvent();
        event.setEventId(eventId);
        event.setUserId(userId);
        event.setEventType(eventType.name());
        event.setSource(source.name());
        event.setSpuId(spuId);
        event.setSkuId(skuId);
        event.setThreadId(normalizeOptionalIdentifier(threadId, "threadId"));
        event.setRequestId(normalizeOptionalIdentifier(requestId, "requestId"));
        event.setOrderNo(normalizeOptionalIdentifier(orderNo, "orderNo"));
        event.setRecommendationId(recommendationId);
        event.setQuantity(quantity);
        event.setEventTime(LocalDateTime.now());
        event.setMetadataJson(toMetadataJson(metadata));
        return event;
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }

    private String normalizeEventId(String rawEventId) {
        String eventId = StringUtils.hasText(rawEventId) ? rawEventId.trim() : UUID.randomUUID().toString();
        if (eventId.length() > MAX_IDENTIFIER_LENGTH) {
            throw new BadRequestException("eventId must not exceed 64 characters");
        }
        return eventId;
    }

    private String normalizeOptionalIdentifier(String rawValue, String fieldName) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String value = rawValue.trim();
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new BadRequestException(fieldName + " must not exceed 64 characters");
        }
        return value;
    }

    private String toMetadataJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("metadata must be JSON serializable");
        }
    }

    private void validateOwnedSelectedItem(Long userId, Long spuId, Long skuId, String recommendationId) {
        if (recommendationId == null) {
            return;
        }
        if (spuId == null || recommendationAttributionMapper.existsOwnedSelectedItem(
                recommendationId, userId, spuId, skuId) == 0) {
            throw new BadRequestException("recommendation item is not available for current user");
        }
    }

    private String resolveBusinessRecommendation(
            BehaviorEventCommand command,
            BehaviorEventType eventType
    ) {
        String recommendationId = normalizeOptionalIdentifier(command.recommendationId(), "recommendationId");
        if (recommendationId != null) {
            validateOwnedSelectedItem(command.userId(), command.spuId(), command.skuId(), recommendationId);
            return recommendationId;
        }
        if (eventType == BehaviorEventType.ORDER_CREATED && command.skuId() != null) {
            return recommendationAttributionMapper.findLatestCartRecommendation(
                    command.userId(), command.skuId(), LocalDateTime.now().minusDays(30));
        }
        if (eventType == BehaviorEventType.PAYMENT_SUCCESS
                && command.orderNo() != null && command.skuId() != null) {
            return recommendationAttributionMapper.findOrderRecommendation(
                    command.userId(), command.orderNo(), command.skuId());
        }
        return null;
    }

    private void recordFunnel(BehaviorEventType eventType, String recommendationId, int insertedRows) {
        if (recommendationId == null || insertedRows <= 0) {
            return;
        }
        String stage = switch (eventType) {
            case RECOMMENDATION_CLICKED -> "click";
            case FAVORITE_ADD, RECOMMENDATION_FAVORITE_ADD -> "favorite";
            case CART_ADD, RECOMMENDATION_CART_ADD -> "cart";
            case ORDER_CREATED -> "order";
            case PAYMENT_SUCCESS -> "payment";
            default -> null;
        };
        if (stage != null) {
            applicationMetrics.recordRecommendationFunnel(stage);
        }
    }
}
