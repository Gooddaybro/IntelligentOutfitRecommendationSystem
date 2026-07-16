package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
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
    private final CircuitBreaker circuitBreaker;

    public ResilientDemandIntentParseClient(
            RestDemandIntentParseClient delegate,
            CircuitBreaker demandIntentParserCircuitBreaker
    ) {
        this.delegate = delegate;
        this.circuitBreaker = demandIntentParserCircuitBreaker;
    }

    @Override
    public Optional<LlmDemandParseResponse> parse(LlmDemandParseRequest request) {
        try {
            return circuitBreaker.executeSupplier(() -> delegate.parse(request));
        } catch (RuntimeException exception) {
            LOGGER.warn("Demand intent parser unavailable for request {}", request.requestId());
            return Optional.empty();
        }
    }
}
