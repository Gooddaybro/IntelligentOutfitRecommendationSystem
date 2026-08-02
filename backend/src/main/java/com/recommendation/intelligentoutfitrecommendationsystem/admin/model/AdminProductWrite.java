package com.recommendation.intelligentoutfitrecommendationsystem.admin.model;

/**
 * Write model for admin SPU mutations whose only mutable field is the database-generated id backfilled by MyBatis.
 */
public class AdminProductWrite {
    private Long id;
    private final String spuCode;
    private final String name;
    private final Long categoryId;
    private final String description;
    private final String mainImageUrl;
    private final String status;

    public AdminProductWrite(
            String spuCode,
            String name,
            Long categoryId,
            String description,
            String mainImageUrl,
            String status) {
        this.spuCode = spuCode;
        this.name = name;
        this.categoryId = categoryId;
        this.description = description;
        this.mainImageUrl = mainImageUrl;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSpuCode() {
        return spuCode;
    }

    public String getName() {
        return name;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getDescription() {
        return description;
    }

    public String getMainImageUrl() {
        return mainImageUrl;
    }

    public String getStatus() {
        return status;
    }
}
