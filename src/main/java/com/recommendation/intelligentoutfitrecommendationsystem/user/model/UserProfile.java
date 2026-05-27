package com.recommendation.intelligentoutfitrecommendationsystem.user.model;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserProfile {

    private Long id;

    private Long userId;

    private String nickname;

    private String avatarUrl;

    private String gender;

    private LocalDate birthday;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
