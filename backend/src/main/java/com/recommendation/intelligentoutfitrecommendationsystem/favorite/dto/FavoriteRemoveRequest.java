package com.recommendation.intelligentoutfitrecommendationsystem.favorite.dto;

/**
 * 收藏删除请求体。
 *
 * 当前收藏删除接口主要通过路径参数接收商品 ID，该 DTO 保留给后续请求体方式扩展。
 */
public class FavoriteRemoveRequest {
    private Long prodectId;

    public Long getProdectId() {
        return prodectId;
    }

    public void setProdectId(Long prodectId) {
        this.prodectId = prodectId;
    }
}
