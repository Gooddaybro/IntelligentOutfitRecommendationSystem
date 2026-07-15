package com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.model.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional Outbox 数据边界，消息发布确认与数据库状态通过显式步骤衔接。
 */
@Mapper
public interface OutboxEventMapper {

    int insertOutbox(OutboxEvent event);

    List<OutboxEvent> findPublishable(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int claimOutbox(
            @Param("eventId") String eventId,
            @Param("claimedBy") String claimedBy,
            @Param("now") LocalDateTime now,
            @Param("claimUntil") LocalDateTime claimUntil
    );

    int markPublished(
            @Param("eventId") String eventId,
            @Param("claimedBy") String claimedBy,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    int releaseClaim(
            @Param("eventId") String eventId,
            @Param("claimedBy") String claimedBy,
            @Param("lastError") String lastError
    );
}
