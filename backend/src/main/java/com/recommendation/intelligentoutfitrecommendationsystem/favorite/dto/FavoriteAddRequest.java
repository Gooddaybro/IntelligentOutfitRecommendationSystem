package com.recommendation.intelligentoutfitrecommendationsystem.favorite.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 收藏新增请求体。
 *
 * 商城收藏新增接口的请求体。
 *
 * SPU 与可选 recommendationId 由客户端提供，但用户身份只从 JWT 中取得，避免请求体越过当前用户边界。
 */
public class FavoriteAddRequest {
    @NotNull
    @Positive
    private Long spuId;

    private String recommendationId;

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    public String getRecommendationId() {
        return recommendationId;
    }

    public void setRecommendationId(String recommendationId) {
        this.recommendationId = recommendationId;
    }
}
