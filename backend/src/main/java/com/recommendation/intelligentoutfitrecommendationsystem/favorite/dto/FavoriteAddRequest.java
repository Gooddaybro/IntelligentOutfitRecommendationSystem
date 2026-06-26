package com.recommendation.intelligentoutfitrecommendationsystem.favorite.dto;

/**
 * 收藏新增请求体。
 *
 * 当前收藏接口主要通过路径参数接收商品 ID，该 DTO 保留给后续请求体方式扩展。
 */
public class FavoriteAddRequest {
    private Long productId;

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }
}
