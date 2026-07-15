package com.recommendation.intelligentoutfitrecommendationsystem.common.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

    @Test
    void recordsAiOutcomeLatencyFallbackAndCandidateQuality() {
        metrics.recordAiRequest("sync", "success", Duration.ofMillis(25));
        metrics.recordAiFallback("sync");
        metrics.recordAiCandidateCount(12);
        metrics.recordAiDiscardedReferences(2);

        assertThat(registry.get("app.ai.requests").tags("mode", "sync", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.request.duration").tags("mode", "sync", "outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.fallbacks").tag("mode", "sync").counter().count()).isEqualTo(1);
        assertThat(registry.get("app.ai.candidates").summary().totalAmount()).isEqualTo(12);
        assertThat(registry.get("app.ai.discarded.references").counter().count()).isEqualTo(2);
    }

    @Test
    void recordsRedisCommandOutcomeAndLatency() {
        metrics.recordRedisCommand("get", "hit", Duration.ofMillis(3));

        assertThat(registry.get("app.redis.commands").tags("operation", "get", "outcome", "hit").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.redis.command.duration").tags("operation", "get", "outcome", "hit").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsOrderAndPaymentOutcomesWithoutBusinessIdentifiers() {
        metrics.recordOrderCreation("cart", "replayed");
        metrics.recordPaymentCallback("invalid_signature");

        assertThat(registry.get("app.order.creation").tags("operation", "cart", "outcome", "replayed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.payment.callbacks").tag("outcome", "invalid_signature").counter().count())
                .isEqualTo(1);
    }

    @Test
    void mapsUnexpectedDynamicTagsToBoundedOtherValue() {
        metrics.recordPaymentCallback("ORD-USER-CONTROLLED-123");

        assertThat(registry.get("app.payment.callbacks").tag("outcome", "other").counter().count())
                .isEqualTo(1);
    }

    @Test
    void recordsBoundedCircuitBreakerStateTransition() {
        metrics.recordAiCircuitTransition("CLOSED", "OPEN");

        assertThat(registry.get("app.ai.circuit.transitions")
                .tags("from", "closed", "to", "open").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsBoundedRecommendationFunnelStage() {
        metrics.recordRecommendationFunnel("exposure");
        metrics.recordRecommendationFunnel("user-controlled-id");

        assertThat(registry.get("app.recommendation.funnel").tag("stage", "exposure").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.recommendation.funnel").tag("stage", "other").counter().count())
                .isEqualTo(1);
    }

    @Test
    void recordsAiTaskMetricsWithOnlyFixedCardinalityTags() {
        metrics.recordAiTask("RAG_REBUILD", "success", Duration.ofSeconds(2));
        metrics.recordAiTaskPublish("confirmed");
        metrics.recordAiTaskConsume("retry", Duration.ofMillis(50));
        metrics.recordAiTaskRetry("2");
        metrics.recordAiTask("task-user-controlled", "event-user-controlled", Duration.ZERO);

        assertThat(registry.get("app.ai.task.executions")
                .tags("taskType", "rag_rebuild", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.task.publish").tag("outcome", "confirmed").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.task.consume").tag("outcome", "retry").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.task.retries").tag("stage", "2").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.task.executions")
                .tags("taskType", "other", "outcome", "other").counter().count())
                .isEqualTo(1);
        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .noneMatch(tag -> tag.getKey().toLowerCase().contains("id")));
    }
}
