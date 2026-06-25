package com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Persistence boundary for current-user favorite SPU relationships.
 */
@Mapper
public interface FavoriteMapper {

    int existsSpuById(@Param("spuId") Long spuId);

    UserFavorite selectByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId);

    List<UserFavorite> selectByUserId(@Param("userId") Long userId);

    int insert(UserFavorite userFavorite);

    int deleteByUserIdAndSpuId(@Param("userId") Long userId, @Param("spuId") Long spuId);

    int countBySpuId(@Param("spuId") Long spuId);

    /**
     * Compatibility method for the early demo service. New code should use SPU naming.
     */
    @Deprecated
    default UserFavorite selectByUserIdAndProductId(Long userId, Long productId) {
        return selectByUserIdAndSpuId(userId, productId);
    }
}
