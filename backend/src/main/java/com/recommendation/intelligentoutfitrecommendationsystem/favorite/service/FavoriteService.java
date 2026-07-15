package com.recommendation.intelligentoutfitrecommendationsystem.favorite.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
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

    public FavoriteService(FavoriteMapper favoriteMapper, BehaviorEventService behaviorEventService) {
        this.favoriteMapper = favoriteMapper;
        this.behaviorEventService = behaviorEventService;
    }

    public List<UserFavorite> addFavorite(Long userId, Long productId) {
        return addFavorite(userId, productId, null);
    }

    public List<UserFavorite> addFavorite(Long userId, Long productId, String recommendationId) {
        UserFavorite existing = favoriteMapper.selectByUserIdAndProductId(userId, productId);
        if (existing != null) {
            return favoriteMapper.selectByUserId(userId);
        }
        UserFavorite userFavorite = new UserFavorite();
        userFavorite.setUserId(userId);
        userFavorite.setProductId(productId);
        userFavorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(userFavorite);
        behaviorEventService.recordBusinessEvent(new BehaviorEventCommand(
                "favorite:add:" + userId + ":" + productId,
                userId,
                "FAVORITE_ADD",
                null,
                productId,
                null,
                null,
                null,
                null,
                null,
                null,
                recommendationId
        ));
        return favoriteMapper.selectByUserId(userId);
    }

    public List<UserFavorite> deleteFavorite(Long userId, Long spuId) {
        validateUserId(userId);
        validateSpuId(spuId);

        favoriteMapper.deleteByUserIdAndSpuId(userId, spuId);
        return favoriteMapper.selectByUserId(userId);
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
