package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.math.BigDecimal;

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
