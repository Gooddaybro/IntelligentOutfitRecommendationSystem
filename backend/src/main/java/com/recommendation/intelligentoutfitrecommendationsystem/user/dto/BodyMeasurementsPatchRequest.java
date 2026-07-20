package com.recommendation.intelligentoutfitrecommendationsystem.user.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

import java.math.BigDecimal;

/** Narrow, optional-field request for explicitly saving only height and weight. */
public record BodyMeasurementsPatchRequest(
        @DecimalMin("100.0")
        @DecimalMax("230.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal heightCm,

        @DecimalMin("25.0")
        @DecimalMax("300.0")
        @Digits(integer = 3, fraction = 2)
        BigDecimal weightKg
) {
}
