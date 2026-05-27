package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 当前用户穿衣偏好入参。
 *
 * 风格、颜色和品类都保留为列表，便于后续直接作为 AI 推荐上下文传给 Python 服务。
 */
public record UserPreferencesRequest(
        @Size(max = 20)
        List<@Size(max = 64) String> preferredStyles,

        @Size(max = 20)
        List<@Size(max = 64) String> preferredColors,

        @Size(max = 20)
        List<@Size(max = 64) String> dislikedColors,

        @Size(max = 20)
        List<@Size(max = 64) String> preferredCategories,

        @DecimalMin("0.0")
        BigDecimal budgetMin,

        @DecimalMin("0.0")
        BigDecimal budgetMax
) {
}
