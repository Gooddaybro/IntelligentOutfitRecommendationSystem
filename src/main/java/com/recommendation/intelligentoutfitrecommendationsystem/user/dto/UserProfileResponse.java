package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import java.time.LocalDate;

public record UserProfileResponse(
        Long userId,
        String nickname,
        String avatarUrl,
        String gender,
        LocalDate birthday
) {
}
