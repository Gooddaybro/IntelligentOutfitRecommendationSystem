package com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.BehaviorProductSignal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户行为事件持久化边界。
 *
 * Java 后端统一记录行为事实，后续推荐摘要和 MQ 演进都从这个边界扩展。
 */
@Mapper
public interface BehaviorMapper {

    int insert(BehaviorEvent event);

    List<BehaviorProductSignal> findRecentSignals(
            @Param("userId") Long userId,
            @Param("since") LocalDateTime since,
            @Param("limit") int limit
    );
}
