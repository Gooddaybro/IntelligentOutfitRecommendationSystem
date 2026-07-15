package com.recommendation.intelligentoutfitrecommendationsystem.aitask.messaging;

import com.recommendation.intelligentoutfitrecommendationsystem.aitask.client.RagRebuildClientException;
import org.springframework.stereotype.Component;

/**
 * 将 Worker 故障收敛为固定三段 Retry、最终 DLQ 或保留未 ACK 三种动作。
 */
@Component
public class AiTaskFailureClassifier {

    /**
     * Worker 可执行的有限故障动作。
     */
    public enum Action {
        RETRY,
        DLQ,
        NO_ACK
    }

    /**
     * 分类结果只暴露安全错误码、摘要和受控的下一重试阶段。
     */
    public record Decision(Action action, int nextRetryStage, String code, String summary) {
    }

    public Decision classify(RuntimeException exception, int retryStage) {
        if (exception instanceof RagRebuildClientException clientException) {
            if (clientException.retryable() && retryStage < 3) {
                return decision(Action.RETRY, retryStage + 1, clientException.code(), exception);
            }
            return decision(Action.DLQ, retryStage, clientException.code(), exception);
        }
        if (exception instanceof IllegalArgumentException) {
            return decision(Action.DLQ, retryStage, "INVALID_MESSAGE", exception);
        }
        if ("AI task lease is held by another worker".equals(exception.getMessage()) && retryStage < 3) {
            return decision(Action.RETRY, retryStage + 1, "LEASE_BUSY", exception);
        }
        return decision(Action.NO_ACK, retryStage, "INFRASTRUCTURE_ERROR", exception);
    }

    private Decision decision(Action action, int stage, String code, RuntimeException exception) {
        String summary = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        if (summary.length() > 500) {
            summary = summary.substring(0, 500);
        }
        return new Decision(action, stage, code, summary);
    }
}
