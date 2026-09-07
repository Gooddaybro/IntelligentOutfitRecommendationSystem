package com.recommendation.intelligentoutfitrecommendationsystem.favorite.service;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventCommand;
import com.recommendation.intelligentoutfitrecommendationsystem.behavior.service.BehaviorEventService;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.mapper.FavoriteMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.FavoriteProduct;
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

    public FavoriteService(
            FavoriteMapper favoriteMapper,
            BehaviorEventService behaviorEventService
    ) {
        this.favoriteMapper = favoriteMapper;
        this.behaviorEventService = behaviorEventService;
    }

    /**
     * 读取当前用户的完整收藏展示列表。
     *
     * 收藏关系与当前可购买性分离：下架或缺货商品仍会返回，保证用户始终可见并删除自己的收藏。
     *
     * @param userId JWT 中解析出的当前用户标识
     * @return 当前用户按收藏时间倒序的商品展示投影
     */
    public List<FavoriteProduct> listFavorites(Long userId) {
        validateUserId(userId);
        return favoriteMapper.selectFavoriteProductsByUserId(userId);
    }

    /**
     * 新增当前用户对 SPU 的收藏，并返回更新后的完整收藏列表。
     *
     * SPU 存在性在写入前校验，避免依赖外键异常向客户端泄漏为 500；重复收藏不重复写行为事件，
     * 但仍以成功列表响应维持幂等体验。
     *
     * @param userId JWT 中解析出的当前用户标识
     * @param spuId 要收藏的商品 SPU 标识
     * @param recommendationId 交由下游归因服务验证归属的可选推荐标识
     * @return 当前用户更新后的完整收藏列表
     * @throws ResourceNotFoundException 当 SPU 不存在时抛出
     */
    public List<FavoriteProduct> addFavorite(Long userId, Long spuId, String recommendationId) {
        validateUserId(userId);
        validateSpuId(spuId);
        if (favoriteMapper.existsSpuById(spuId) == 0) {
            throw new ResourceNotFoundException("product not found: " + spuId);
        }

        UserFavorite existing = favoriteMapper.selectByUserIdAndSpuId(userId, spuId);
        if (existing != null) {
            return listFavorites(userId);
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
                recommendationId
        ));
        return listFavorites(userId);
    }

    /**
     * 删除当前用户指定 SPU 的收藏，并返回更新后的完整收藏列表。
     *
     * 删除条件包含 userId，因此不会影响其他用户；没有对应关系时不报错，保持 DELETE 幂等。
     *
     * @param userId JWT 中解析出的当前用户标识
     * @param spuId 要删除的商品 SPU 标识
     * @return 当前用户删除后的完整收藏列表
     */
    public List<FavoriteProduct> deleteFavorite(Long userId, Long spuId) {
        validateUserId(userId);
        validateSpuId(spuId);

        favoriteMapper.deleteByUserIdAndSpuId(userId, spuId);
        return listFavorites(userId);
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
