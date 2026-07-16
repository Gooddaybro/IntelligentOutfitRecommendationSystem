package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.LlmDemandParseRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientDemandIntentParseClientTests {

    @Test
    void opensItsOwnCircuitAndReturnsCapabilityMiss() {
        RestDemandIntentParseClient delegate = mock(RestDemandIntentParseClient.class);
        LlmDemandParseRequest request = mock(LlmDemandParseRequest.class);
        when(request.requestId()).thenReturn("req-parser");
        when(delegate.parse(request)).thenThrow(new IllegalStateException("down"));
        CircuitBreaker breaker = CircuitBreaker.of("parser-test", CircuitBreakerConfig.custom()
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100)
                .build());
        ResilientDemandIntentParseClient client = new ResilientDemandIntentParseClient(delegate, breaker);

        assertThat(client.parse(request)).isEqualTo(Optional.empty());
        assertThat(client.parse(request)).isEqualTo(Optional.empty());
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(client.parse(request)).isEqualTo(Optional.empty());
        verify(delegate, times(2)).parse(request);
    }
}
