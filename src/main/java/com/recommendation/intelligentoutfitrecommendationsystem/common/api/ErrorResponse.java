package com.recommendation.intelligentoutfitrecommendationsystem.common.api;

public record ErrorResponse(
        String errorCode,
        String message
) {
}
