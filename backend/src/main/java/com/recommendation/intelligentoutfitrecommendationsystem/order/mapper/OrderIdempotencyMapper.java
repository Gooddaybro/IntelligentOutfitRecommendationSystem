package com.recommendation.intelligentoutfitrecommendationsystem.order.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderIdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单幂等占位、结果关联和过期清理的数据访问边界。
 *
 * `(user_id, operation, idempotency_key)` 唯一约束是并发重复下单的最终防线，调用方
 * 必须把占位和订单创建放在同一个 MySQL 事务中。
 */
@Mapper
public interface OrderIdempotencyMapper {

    void insert(OrderIdempotencyRecord record);

    int deleteExpiredKey(
            @Param("userId") Long userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("now") LocalDateTime now
    );

    OrderIdempotencyRecord findByKey(
            @Param("userId") Long userId,
            @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey
    );

    int linkOrder(@Param("id") Long id, @Param("orderId") Long orderId);

    List<Long> findExpiredIds(@Param("now") LocalDateTime now, @Param("batchSize") int batchSize);

    int deleteByIds(@Param("ids") List<Long> ids);
}
