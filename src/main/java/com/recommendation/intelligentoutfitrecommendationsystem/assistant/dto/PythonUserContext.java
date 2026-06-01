package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Python `/chat` 用户画像契约，把 Java 的基础资料、身体数据和穿衣偏好合并为扁平上下文。
 */
public record PythonUserContext(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("height_cm") BigDecimal heightCm,
        @JsonProperty("weight_kg") BigDecimal weightKg,
        @JsonProperty("gender") String gender,
        @JsonProperty("preferred_fit") String preferredFit,
        @JsonProperty("preferred_styles") List<String> preferredStyles,
        @JsonProperty("preferred_colors") List<String> preferredColors,
        @JsonProperty("disliked_colors") List<String> dislikedColors,
        @JsonProperty("preferred_categories") List<String> preferredCategories,
        @JsonProperty("budget_min") BigDecimal budgetMin,
        @JsonProperty("budget_max") BigDecimal budgetMax
) {
}
