package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.math.BigDecimal;
import java.util.List;

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
