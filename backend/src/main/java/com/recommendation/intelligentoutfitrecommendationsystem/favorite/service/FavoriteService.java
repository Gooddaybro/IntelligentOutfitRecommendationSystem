package com.recommendation.intelligentoutfitrecommendationsystem.favorite.service;

import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;

import java.time.LocalDateTime;
import java.util.List;

public class FavoriteService {
    private final FavoriteMapper favoriteMapper;

    public FavoriteService(FavoriteMapper favoriteMapper) {
        this.favoriteMapper = favoriteMapper;
    }

    public List<UserFavorite> addFavorite(Long userId, Long productId) {
        UserFavorite existing= favoriteMapper.selectByUserIdAndProdectId(userId,productId);
        if(existing==null){
            return;
        }
        UserFavorite userFavorite=new UserFavorite();
        userFavorite.setUserId(userId);
        userFavorite.setProductId(productId);
        userFavorite.setCreatedAt(LocalDateTime.now());
        favoriteMapper.insert(userFavorite);
    }
}
