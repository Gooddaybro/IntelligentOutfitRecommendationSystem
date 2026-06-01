package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.math.BigDecimal;

/**
 * 用户身体数据响应，供前端资料页和 AI 推荐上下文读取尺码画像。
 */
public record UserBodyDataResponse(
        Long userId,
        BigDecimal heightCm,
        BigDecimal weightKg,
        String gender,
        BigDecimal shoulderWidthCm,
        BigDecimal bustCm,
        BigDecimal waistCm,
        BigDecimal hipCm,
        String preferredFit
) {
}
