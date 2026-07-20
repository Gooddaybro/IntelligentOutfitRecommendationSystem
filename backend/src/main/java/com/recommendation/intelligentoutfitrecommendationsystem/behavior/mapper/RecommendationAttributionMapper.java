package com.recommendation.intelligentoutfitrecommendationsystem.behavior.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.model.RecommendationSnapshot;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

/**
 * 推荐快照的持久化边界。
 *
 * 父记录和全部候选子项由同一个事务服务写入，避免只保存 recommendationId 却缺失候选证据。
 */
@Mapper
public interface RecommendationAttributionMapper {

    int insertRecommendation(RecommendationSnapshot snapshot);

    int insertItems(
            @Param("recommendationId") String recommendationId,
            @Param("items") List<RecommendationSnapshot.Item> items
    );

    int existsOwnedRecommendation(
            @Param("recommendationId") String recommendationId,
            @Param("userId") Long userId
    );

    List<RecommendationCandidate> findOwnedCandidates(
            @Param("recommendationId") String recommendationId,
            @Param("userId") Long userId
    );

    int existsOwnedSelectedItem(
            @Param("recommendationId") String recommendationId,
            @Param("userId") Long userId,
            @Param("spuId") Long spuId,
            @Param("skuId") Long skuId
    );

    String findLatestCartRecommendation(
            @Param("userId") Long userId,
            @Param("skuId") Long skuId,
            @Param("since") LocalDateTime since
    );

    String findOrderRecommendation(
            @Param("userId") Long userId,
            @Param("orderNo") String orderNo,
            @Param("skuId") Long skuId
    );
}
