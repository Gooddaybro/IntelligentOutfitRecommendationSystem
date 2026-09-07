package com.recommendation.intelligentoutfitrecommendationsystem.favorite.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 收藏新增请求体。
 *
 * 商城收藏新增接口的请求体。
 *
 * SPU 由客户端选择，但用户身份只从 JWT 中取得，避免请求体越过当前用户边界。
 */
public class FavoriteAddRequest {
    @NotNull
    @Positive
    private Long spuId;

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }
}
