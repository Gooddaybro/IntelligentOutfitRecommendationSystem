package com.recommendation.intelligentoutfitrecommendationsystem.favorite.model;

import java.time.LocalDateTime;

/**
 * Current user's favorite relationship with a product SPU.
 */
public class UserFavorite {
    private Long id;
    private Long userId;
    private Long spuId;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    /**
     * Compatibility accessor for the early favorite demo that used productId
     * while the database table stores the relationship by SPU.
     */
    @Deprecated
    public Long getProductId() {
        return spuId;
    }

    /**
     * Compatibility mutator for existing service code until it is renamed to spuId.
     */
    @Deprecated
    public void setProductId(Long productId) {
        this.spuId = productId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
