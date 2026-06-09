package com.recommendation.intelligentoutfitrecommendationsystem.user.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户穿衣偏好持久化模型。
 *
 * preferredStyles、preferredColors 等字段以 JSON 文本存储，
 * Service 层负责和接口 DTO 的 List<String> 相互转换。
 */
@Data
public class UserPreferences {

    private Long id;

    private Long userId;

    private String preferredStyles;

    private String preferredColors;

    private String dislikedColors;

    private String preferredCategories;

    private BigDecimal budgetMin;

    private BigDecimal budgetMax;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
