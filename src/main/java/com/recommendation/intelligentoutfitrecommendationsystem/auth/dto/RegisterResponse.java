package com.recommendation.intelligentoutfitrecommendationsystem.auth.dto;

public record RegisterResponse(
        Long userId,
        String username,
        String status
) {
}
