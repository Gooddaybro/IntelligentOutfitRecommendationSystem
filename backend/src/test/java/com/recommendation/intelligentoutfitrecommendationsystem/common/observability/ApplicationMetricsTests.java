package com.recommendation.intelligentoutfitrecommendationsystem.common.observability;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.RecommendationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationMetricsTests {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

    @Test
    void aiSelectionRequiresTheRecommendationStatusType() throws Exception {
        assertThat(ApplicationMetrics.class.getDeclaredMethod(
                "recordAiSelection", int.class, int.class, int.class, RecommendationStatus.class))
                .isNotNull();
    }

    @Test
    void recordsAiOutcomeLatencyFallbackAndCandidateQuality() {
        metrics.recordAiRequest("sync", "success", Duration.ofMillis(25));
        metrics.recordAiFallback("sync");
        metrics.recordAiCandidateCount(12);
        metrics.recordAiDiscardedReferences(2);
        metrics.recordAiSelection(24, 0, 0, RecommendationStatus.BROWSE_FALLBACK);
        metrics.recordAiReasonCode("PYTHON_REJECTED_ALL");
        metrics.recordAiReasonCode("raw user query must never become a tag");

        assertThat(registry.get("app.ai.requests").tags("mode", "sync", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.request.duration").tags("mode", "sync", "outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("app.ai.fallbacks").tag("mode", "sync").counter().count()).isEqualTo(1);
        assertThat(registry.get("app.ai.candidates").summary().totalAmount()).isEqualTo(12);
        assertThat(registry.get("app.ai.discarded.references").counter().count()).isEqualTo(2);
        assertThat(registry.get("app.ai.selection")
                .tag("status", "BROWSE_FALLBACK").counter().count()).isEqualTo(1);
        assertThat(registry.get("app.ai.selection.java.candidates").summary().totalAmount()).isEqualTo(24);
        assertThat(registry.get("app.ai.selection.python.selected").summary().totalAmount()).isZero();
        assertThat(registry.get("app.ai.selection.java.accepted").summary().totalAmount()).isZero();
        assertThat(registry.get("app.ai.reason")
                .tag("code", "PYTHON_REJECTED_ALL").counter().count()).isEqualTo(1);
        assertThat(registry.get("app.ai.reason").tag("code", "other").counter().count()).isEqualTo(1);
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

    @Test
    void recordsProductSearchMetricsWithOnlyFixedCardinalityTags() {
        metrics.recordProductSearchEngine("ELASTICSEARCH", "success", Duration.ofMillis(12));
        metrics.recordProductSearchFallback("unavailable");
        metrics.recordProductSearchSyncConsume("retry", Duration.ofMillis(30));
        metrics.recordProductSearchSyncRetry("2");
        metrics.recordProductSearchRebuild("success", Duration.ofSeconds(3));
        metrics.recordProductSearchRebuildBulkFailures(4);
        metrics.recordProductSearchRebuildDocumentDrift(6);

        metrics.recordProductSearchEngine("keyword-user-controlled", "event-123", Duration.ZERO);
        metrics.recordProductSearchSyncConsume("event-123", Duration.ZERO);
        metrics.recordProductSearchRebuild("product_20260721000000", Duration.ZERO);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.engine.duration")
                .tags("engine", "elasticsearch", "outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.fallbacks")
                .tag("reason", "unavailable").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.sync.consume")
                .tag("outcome", "retry").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.sync.consume.duration")
                .tag("outcome", "retry").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.sync.retries")
                .tag("stage", "2").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.rebuild.executions")
                .tag("outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.rebuild.duration")
                .tag("outcome", "success").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.rebuild.bulk.failures").counter().count())
                .isEqualTo(4);
        assertThat(registry.get("app.product.search.rebuild.document.drift").summary().totalAmount())
                .isEqualTo(6);
        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "other", "outcome", "other").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.sync.consume")
                .tag("outcome", "other").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.rebuild.executions")
                .tag("outcome", "other").counter().count())
                .isEqualTo(1);
    }
}
