package com.recommendation.intelligentoutfitrecommendationsystem.favorite.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.dto.FavoriteAddRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.service.FavoriteService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping
    public ApiResponse<List<RecommendationCandidate>> listFavorites(Authentication authentication) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(favoriteService.listFavorites(currentUser.userId()));
    }

    @PostMapping
    public ApiResponse<List<RecommendationCandidate>> addFavorite(
            Authentication authentication,
            @Valid @RequestBody FavoriteAddRequest request
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(favoriteService.addFavorite(currentUser.userId(), request.getSpuId()));
    }

    @DeleteMapping("/{spuId}")
    public ApiResponse<List<RecommendationCandidate>> deleteFavorite(
            Authentication authentication,
            @PathVariable Long spuId
    ) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        return ApiResponse.ok(favoriteService.deleteFavorite(currentUser.userId(), spuId));
    }
}
