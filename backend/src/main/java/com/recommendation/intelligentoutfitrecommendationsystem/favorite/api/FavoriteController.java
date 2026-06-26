package com.recommendation.intelligentoutfitrecommendationsystem.favorite.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.service.FavoriteService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户收藏接口。
 *
 * 收藏属于当前登录用户的个人行为数据，Controller 只从鉴权上下文读取 userId，
 * 不允许客户端传入 userId 操作其他用户收藏。
 */
@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /**
     * 添加收藏数据
     *
     * @param authentication
     * @param productId
     * @return
     */
    @PostMapping("/{productId}")
    public ApiResponse<List<UserFavorite>> addFavoriteIrem(Authentication authentication, @PathVariable Long productId) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        List<UserFavorite> currentUsers = favoriteService.addFavorite(currentUser.userId(), productId);
        return ApiResponse.ok(currentUsers);
    }

    /**
     * 删除收藏数据
     *
     * @param authentication
     * @param spuId
     * @return
     */
    @DeleteMapping("/{spuId}")
    public ApiResponse<List<UserFavorite>> deleteFavorite(Authentication authentication, @PathVariable Long spuId) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        List<UserFavorite> cur = favoriteService.deleteFavorite(currentUser.userId(), spuId);
        return ApiResponse.ok(cur);
    }


}
