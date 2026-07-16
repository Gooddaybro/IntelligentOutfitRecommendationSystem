package com.recommendation.intelligentoutfitrecommendationsystem.common.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.Locale;

/**
 * 应用核心业务指标的低基数出口。
 *
 * <p>业务服务只提交固定操作和结果，未知标签会收敛为 {@code other}，避免用户输入、订单号或
 * request ID 进入 Prometheus 标签并造成时序数量失控。</p>
 */
@Component
public class ApplicationMetrics {

    private static final Set<String> AI_MODES = Set.of("sync", "stream");
    private static final Set<String> AI_OUTCOMES = Set.of("success", "error", "circuit_open");
    private static final Set<String> REDIS_OPERATIONS = Set.of("get", "set", "delete", "increment");
    private static final Set<String> REDIS_OUTCOMES = Set.of("hit", "miss", "success", "error");
    private static final Set<String> ORDER_OPERATIONS = Set.of("cart", "buy_now");
    private static final Set<String> ORDER_OUTCOMES = Set.of("created", "replayed", "failed");
    private static final Set<String> PAYMENT_OUTCOMES = Set.of(
            "success", "invalid_signature", "duplicate", "rejected");
    private static final Set<String> CIRCUIT_STATES = Set.of(
            "closed", "open", "half_open", "disabled", "forced_open", "metrics_only");
    private static final Set<String> RECOMMENDATION_STAGES = Set.of(
            "exposure", "click", "favorite", "cart", "order", "payment");
    private static final Set<String> AI_TASK_TYPES = Set.of("rag_rebuild");
    private static final Set<String> AI_TASK_OUTCOMES = Set.of(
            "created", "replayed", "success", "failed");
    private static final Set<String> AI_TASK_PUBLISH_OUTCOMES = Set.of(
            "confirmed", "nack", "returned", "timeout", "error");
    private static final Set<String> AI_TASK_CONSUME_OUTCOMES = Set.of(
            "success", "duplicate", "retry", "dlq", "error");
    private static final Set<String> AI_TASK_RETRY_STAGES = Set.of("1", "2", "3");

    private final MeterRegistry registry;
    private final DistributionSummary candidateSummary;

    public ApplicationMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.candidateSummary = DistributionSummary.builder("app.ai.candidates")
                .description("Candidate products assembled by Java for an AI request")
                .register(registry);
    }

    public void recordAiRequest(String mode, String outcome, Duration duration) {
        String safeMode = bounded(mode, AI_MODES);
        String safeOutcome = bounded(outcome, AI_OUTCOMES);
        registry.counter("app.ai.requests", "mode", safeMode, "outcome", safeOutcome).increment();
        registry.timer("app.ai.request.duration", "mode", safeMode, "outcome", safeOutcome)
                .record(nonNegative(duration));
    }

    public void recordAiFallback(String mode) {
        registry.counter("app.ai.fallbacks", "mode", bounded(mode, AI_MODES)).increment();
    }

    public void recordAiCandidateCount(int count) {
        candidateSummary.record(Math.max(0, count));
    }

    public void recordAiDiscardedReferences(int count) {
        registry.counter("app.ai.discarded.references").increment(Math.max(0, count));
    }

    public void recordRedisCommand(String operation, String outcome, Duration duration) {
        String safeOperation = bounded(operation, REDIS_OPERATIONS);
        String safeOutcome = bounded(outcome, REDIS_OUTCOMES);
        registry.counter("app.redis.commands", "operation", safeOperation, "outcome", safeOutcome).increment();
        registry.timer("app.redis.command.duration", "operation", safeOperation, "outcome", safeOutcome)
                .record(nonNegative(duration));
    }

    public void recordOrderCreation(String operation, String outcome) {
        registry.counter(
                "app.order.creation",
                "operation", bounded(operation, ORDER_OPERATIONS),
                "outcome", bounded(outcome, ORDER_OUTCOMES)
        ).increment();
    }

    public void recordPaymentCallback(String outcome) {
        registry.counter("app.payment.callbacks", "outcome", bounded(outcome, PAYMENT_OUTCOMES)).increment();
    }

    public void recordAiCircuitTransition(String from, String to) {
        registry.counter(
                "app.ai.circuit.transitions",
                "from", bounded(normalize(from), CIRCUIT_STATES),
                "to", bounded(normalize(to), CIRCUIT_STATES)
        ).increment();
    }

    public void recordDemandParserCircuitTransition(String from, String to) {
        registry.counter(
                "app.ai.demand_parser.circuit.transitions",
                "from", bounded(normalize(from), CIRCUIT_STATES),
                "to", bounded(normalize(to), CIRCUIT_STATES)
        ).increment();
    }

    public void recordRecommendationFunnel(String stage) {
        registry.counter("app.recommendation.funnel", "stage", bounded(stage, RECOMMENDATION_STAGES))
                .increment();
    }

    public void recordAiTask(String taskType, String outcome, Duration duration) {
        String safeType = bounded(normalize(taskType), AI_TASK_TYPES);
        String safeOutcome = bounded(normalize(outcome), AI_TASK_OUTCOMES);
        registry.counter(
                "app.ai.task.executions", "taskType", safeType, "outcome", safeOutcome
        ).increment();
        registry.timer(
                "app.ai.task.duration", "taskType", safeType, "outcome", safeOutcome
        ).record(nonNegative(duration));
    }

    public void recordAiTaskPublish(String outcome) {
        registry.counter(
                "app.ai.task.publish",
                "outcome", bounded(normalize(outcome), AI_TASK_PUBLISH_OUTCOMES)
        ).increment();
    }

    public void recordAiTaskConsume(String outcome, Duration duration) {
        String safeOutcome = bounded(normalize(outcome), AI_TASK_CONSUME_OUTCOMES);
        registry.counter("app.ai.task.consume", "outcome", safeOutcome).increment();
        registry.timer("app.ai.task.consume.duration", "outcome", safeOutcome)
                .record(nonNegative(duration));
    }

    public void recordAiTaskRetry(String stage) {
        registry.counter(
                "app.ai.task.retries",
                "stage", bounded(stage, AI_TASK_RETRY_STAGES)
        ).increment();
    }

    private String bounded(String value, Set<String> allowed) {
        return allowed.contains(value) ? value : "other";
    }

    private Duration nonNegative(Duration duration) {
        return duration == null || duration.isNegative() ? Duration.ZERO : duration;
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
