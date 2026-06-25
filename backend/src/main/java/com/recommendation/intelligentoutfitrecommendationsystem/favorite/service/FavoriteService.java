package com.recommendation.intelligentoutfitrecommendationsystem.favorite.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FavoriteService {
    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public List<UserFavorite> addFavorite(Long userId, Long productId) {
        UserFavorite existing = favoriteMapper.selectByUserIdAndProductId(userId, productId);
        if (existing != null) {
            return favoriteMapper.selectByUserId(userId);
        }
        UserFavorite userFavorite = new UserFavorite();
        userFavorite.setUserId(userId);
        userFavorite.setProductId(productId);
        userFavorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(userFavorite);
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
