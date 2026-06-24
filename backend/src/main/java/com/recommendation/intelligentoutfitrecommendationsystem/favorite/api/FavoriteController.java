package com.recommendation.intelligentoutfitrecommendationsystem.favorite.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.model.UserFavorite;
import com.recommendation.intelligentoutfitrecommendationsystem.favorite.service.FavoriteService;
import com.recommendation.intelligentoutfitrecommendationsystem.security.CurrentUser;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping("/{productId}")
    public ApiResponse<List<UserFavorite>> addFavoriteIrem(Authentication authentication, @PathVariable Long productId) {
        CurrentUser currentUser = CurrentUser.from(authentication);
        List<CurrentUser> currentUsers = favoriteService.addFavorite(currentUser.userId(), productId);
        return ApiResponse.ok(currentUsers);
    }


}