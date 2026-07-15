package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.config.AssistantResilienceConfig;
import com.recommendation.intelligentoutfitrecommendationsystem.common.observability.ApplicationMetrics;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantResilienceConfigTests {

    @Test
    void exposesCurrentCircuitStateAndTransitions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApplicationMetrics metrics = new ApplicationMetrics(registry);
        AssistantResilienceConfig config = new AssistantResilienceConfig(registry, metrics);
        CircuitBreaker circuitBreaker = config.pythonAssistantCircuitBreaker(2, 2, 100, 30000, 1);

        assertThat(registry.get("app.ai.circuit.state").gauge().value()).isZero();

        circuitBreaker.transitionToOpenState();

        assertThat(registry.get("app.ai.circuit.state").gauge().value()).isEqualTo(1);
        assertThat(registry.get("app.ai.circuit.transitions")
                .tags("from", "closed", "to", "open").counter().count()).isEqualTo(1);
    }
}
