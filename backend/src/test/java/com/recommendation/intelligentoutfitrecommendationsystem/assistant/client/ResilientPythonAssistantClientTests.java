package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonUserContext;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResilientPythonAssistantClientTests {

    @Test
    void opensAfterConfiguredFailuresAndStopsCallingDelegate() {
        RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
        CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofSeconds(30));
        ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
        PythonChatRequest request = minimalPythonRequest();
        when(delegate.chat(request)).thenThrow(new ExternalServiceException("down"));

        assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
        assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThatThrownBy(() -> client.chat(request)).isInstanceOf(CallNotPermittedException.class);
        verify(delegate, times(2)).chat(request);
    }

    @Test
    void successfulHalfOpenProbeClosesCircuitWithoutRestart() throws Exception {
        RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
        CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofMillis(10));
        ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
        PythonChatRequest request = minimalPythonRequest();
        PythonChatResponse success = new PythonChatResponse("req", "ok", "chat", List.of());
        when(delegate.chat(request))
                .thenThrow(new ExternalServiceException("down"))
                .thenThrow(new ExternalServiceException("down"))
                .thenReturn(success);

        assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
        assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
        Thread.sleep(25L);

        assertThat(client.chat(request)).isEqualTo(success);
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void recordsStreamErrorAndRejectsNextStreamWhenCircuitIsOpen() {
        RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
        CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofSeconds(30));
        ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
        PythonAssistantStreamHandler first = mock(PythonAssistantStreamHandler.class);
        PythonAssistantStreamHandler second = mock(PythonAssistantStreamHandler.class);
        doAnswer(invocation -> {
            invocation.<PythonAssistantStreamHandler>getArgument(1)
                    .onError("python_stream_unavailable", "down");
            return null;
        }).when(delegate).streamChat(any(), any());

        client.streamChat(minimalPythonRequest(), first);
        client.streamChat(minimalPythonRequest(), first);
        client.streamChat(minimalPythonRequest(), second);

        verify(second).onError(eq("python_circuit_open"), anyString());
        verify(delegate, times(2)).streamChat(any(), any());
    }

    private CircuitBreaker newCircuitBreaker(Duration openDuration) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(2)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(100.0f)
                .waitDurationInOpenState(openDuration)
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
        return CircuitBreaker.of("python-assistant-test", config);
    }

    private PythonChatRequest minimalPythonRequest() {
        return new PythonChatRequest(
                "req-circuit-test",
                "th_circuit_001",
                "th_circuit_001",
                "hello",
                List.of(),
                new PythonUserContext(
                        10L,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null,
                        null
                ),
                List.of(),
                false
        );
    }
}
