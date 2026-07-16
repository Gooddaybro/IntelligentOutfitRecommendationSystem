package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Converts parser dependency failures into an optional capability miss. */
@Primary
@Component
public class ResilientDemandIntentParseClient implements DemandIntentParseClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResilientDemandIntentParseClient.class);
    private final RestDemandIntentParseClient delegate;

    public ResilientDemandIntentParseClient(RestDemandIntentParseClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<LlmDemandParseResponse> parse(LlmDemandParseRequest request) {
        try {
            return delegate.parse(request);
        } catch (RuntimeException exception) {
            LOGGER.warn("Demand intent parser unavailable for request {}", request.requestId());
            return Optional.empty();
        }
    }
}
