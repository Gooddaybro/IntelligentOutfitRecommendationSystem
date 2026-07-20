package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 消费端幂等记录，防止 RabbitMQ 重投造成重复投影。 */
@Mapper
public interface ProductSearchInboxMapper {
    boolean exists(@Param("consumerName") String consumerName, @Param("eventId") String eventId);

    int insert(@Param("consumerName") String consumerName,
               @Param("eventId") String eventId,
               @Param("spuId") Long spuId,
               @Param("processedAt") LocalDateTime processedAt);
}
