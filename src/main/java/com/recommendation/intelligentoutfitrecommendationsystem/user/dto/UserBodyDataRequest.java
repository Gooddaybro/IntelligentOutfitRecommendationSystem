package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UserBodyDataRequest(
        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal heightCm,

        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weightKg,

        @Size(max = 32)
        String gender,

        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal shoulderWidthCm,

        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal bustCm,

        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal waistCm,

        @DecimalMin("0.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal hipCm,

        @Pattern(regexp = "^(slim|regular|loose|oversized)$")
        String preferredFit
) {
}
