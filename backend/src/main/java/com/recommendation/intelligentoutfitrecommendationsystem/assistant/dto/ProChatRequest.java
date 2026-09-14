package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Lightweight public Pro input; extra frontend facts are deliberately discarded.
 * Identity, history, candidates and run credentials are never fields of this DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProChatRequest(
        @Size(max = 64) String threadId,
        @NotBlank @Size(max = 2000) String message,
        @NotBlank @Pattern(regexp = "pro") String agentMode,
        @Size(max = 100) String category,
        @Size(max = 100) String style,
        @Size(max = 100) String season,
        @Size(max = 100) String material,
        @Size(max = 100) String fit,
        @Size(max = 100) String gender,
        @DecimalMin("0") @DecimalMax("2147483647") @Digits(integer = 10, fraction = 2) BigDecimal budgetMax
) {
}
