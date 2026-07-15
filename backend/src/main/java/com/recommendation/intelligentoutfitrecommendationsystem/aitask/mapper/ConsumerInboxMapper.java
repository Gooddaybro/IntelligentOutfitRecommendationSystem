package com.recommendation.intelligentoutfitrecommendationsystem.aitask.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 消费者幂等记录边界，联合主键阻止同一消费者重复提交同一事件。
 */
@Mapper
public interface ConsumerInboxMapper {

    int insertInbox(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId,
            @Param("taskId") String taskId,
            @Param("processedAt") LocalDateTime processedAt
    );

    boolean exists(
            @Param("consumerName") String consumerName,
            @Param("eventId") String eventId
    );
}
