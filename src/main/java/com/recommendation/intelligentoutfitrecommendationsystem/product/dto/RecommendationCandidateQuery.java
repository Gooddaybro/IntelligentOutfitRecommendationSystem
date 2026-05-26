package com.recommendation.intelligentoutfitrecommendationsystem.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidateQuery {
    private String category;
    private String style;
    private String season;
    private String material;
    private String fit;
    private Integer budgetMax;
}
