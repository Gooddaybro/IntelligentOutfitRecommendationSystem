package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UserProfileRequest(
        @Size(max = 64) String nickname,
        @Size(max = 512) String avatarUrl,
        @Size(max = 32) String gender,
        LocalDate birthday
) {
}
