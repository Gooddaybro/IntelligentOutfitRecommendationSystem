package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 用户穿衣偏好响应，向前端和推荐上下文暴露风格、颜色、品类和预算偏好。
 */
public record UserPreferencesResponse(
        Long userId,
        List<String> preferredStyles,
        List<String> preferredColors,
        List<String> dislikedColors,
        List<String> preferredCategories,
        BigDecimal budgetMin,
        BigDecimal budgetMax
) {
}
