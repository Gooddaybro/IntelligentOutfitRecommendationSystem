package com.recommendation.intelligentoutfitrecommendationsystem.user.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserBodyData {

    private Long id;

    private Long userId;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private String gender;

    private BigDecimal shoulderWidthCm;

    private BigDecimal bustCm;

    private BigDecimal waistCm;

    private BigDecimal hipCm;

    private String preferredFit;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
