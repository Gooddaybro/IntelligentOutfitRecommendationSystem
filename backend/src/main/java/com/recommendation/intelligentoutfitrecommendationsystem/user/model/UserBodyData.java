package com.recommendation.intelligentoutfitrecommendationsystem.user.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户身体数据持久化模型，为尺码推荐和穿搭匹配提供结构化身体画像。
 */
@Data
public class UserBodyData {

    private Long id;

    private Long userId;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String gender;

    private BigDecimal shoulderWidthCm;

    private BigDecimal bustCm;

    private BigDecimal waistCm;

    private BigDecimal hipCm;

    private String preferredFit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
