package com.recommendation.intelligentoutfitrecommendationsystem.favorite.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.RecommendationCandidateQueryService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户收藏服务。
 *
 * 收藏只记录用户对商品 SPU 的轻量偏好，不参与订单、库存和支付事实计算。
 */
@Service
public class FavoriteService {
    private final FavoriteMapper favoriteMapper;

    private final BehaviorEventService behaviorEventService;

    private final RecommendationCandidateQueryService recommendationCandidateQueryService;

    public FavoriteService(
            FavoriteMapper favoriteMapper,
            BehaviorEventService behaviorEventService,
            RecommendationCandidateQueryService recommendationCandidateQueryService
    ) {
        this.favoriteMapper = favoriteMapper;
        this.behaviorEventService = behaviorEventService;
        this.recommendationCandidateQueryService = recommendationCandidateQueryService;
    }

    /**
     * 读取当前用户收藏，并将关系记录转换为商城页面可直接展示的商品候选。
     *
     * 用户过滤必须先在收藏关系查询中完成，商品查询只负责补齐已获授权 SPU 的当前商品事实。
     *
     * @param userId JWT 中解析出的当前用户标识
     * @return 当前用户仍可购买的收藏商品
     */
    public List<RecommendationCandidate> listFavorites(Long userId) {
        validateUserId(userId);
        return displayableFavorites(userId);
    }

    public List<RecommendationCandidate> addFavorite(Long userId, Long spuId) {
        validateUserId(userId);
        validateSpuId(spuId);

        UserFavorite existing = favoriteMapper.selectByUserIdAndSpuId(userId, spuId);
        if (existing != null) {
            return displayableFavorites(userId);
        }
        UserFavorite userFavorite = new UserFavorite();
        userFavorite.setUserId(userId);
        userFavorite.setSpuId(spuId);
        userFavorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(userFavorite);
        behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                "favorite:add:" + userId + ":" + spuId,
                userId,
                "FAVORITE_ADD",
                null,
                spuId,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
        return displayableFavorites(userId);
    }

    public List<RecommendationCandidate> deleteFavorite(Long userId, Long spuId) {
        validateUserId(userId);
        validateSpuId(spuId);

        favoriteMapper.deleteByUserIdAndSpuId(userId, spuId);
        return displayableFavorites(userId);
    }

    private List<RecommendationCandidate> displayableFavorites(Long userId) {
        List<Long> spuIds = favoriteMapper.selectByUserId(userId).stream()
                .map(UserFavorite::getSpuId)
                .toList();
        return recommendationCandidateQueryService.findCandidatesBySpuIds(spuIds);
    }

    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BadRequestException("userId must be positive");
        }
    }

    private void validateSpuId(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
    }
}
