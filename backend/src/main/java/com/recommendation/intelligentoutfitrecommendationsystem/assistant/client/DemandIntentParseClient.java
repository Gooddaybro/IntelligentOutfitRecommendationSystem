package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;

import java.util.Optional;

/** Optional semantic parse capability; transport failure must degrade to an empty result. */
public interface DemandIntentParseClient {
    Optional<LlmDemandParseResponse> parse(LlmDemandParseRequest request);
}
