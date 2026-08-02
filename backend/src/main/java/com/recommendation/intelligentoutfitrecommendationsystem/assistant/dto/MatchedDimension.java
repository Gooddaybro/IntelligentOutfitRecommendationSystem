package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A structured recommendation claim that Java can verify against one candidate fact. */
public record MatchedDimension(
        String dimension,
        @JsonProperty("requested_value") String requestedValue,
        @JsonProperty("candidate_value") String candidateValue,
        @JsonProperty("evidence_source") String evidenceSource
) {
    public MatchedDimension {
        dimension = trim(dimension);
        requestedValue = trim(requestedValue);
        candidateValue = trim(candidateValue);
        evidenceSource = trim(evidenceSource);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
