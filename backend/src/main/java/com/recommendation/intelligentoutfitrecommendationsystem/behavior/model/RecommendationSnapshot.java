package com.recommendation.intelligentoutfitrecommendationsystem.behavior.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一次 AI 推荐的持久化快照。
 *
 * 父记录保存请求身份和规则版本，子项同时保存 Java 候选集与经过可信引用校验后的最终选择，
 * 后续点击和交易事件只能引用这个稳定的 recommendationId。
 */
public record RecommendationSnapshot(
        String recommendationId,
        Long userId,
        String requestId,
        String threadId,
        String mode,
        int candidateCount,
        String ruleVersion,
        LocalDateTime createdAt,
        List<Item> items
) {

    /**
     * 推荐快照中的单个候选 SKU；selected 只表示 AI 最终选择，不代表库存或交易状态。
     */
    public record Item(
            Long spuId,
            Long skuId,
            boolean selected,
            Integer rankPosition,
            BigDecimal rankScore
    ) {
    }
}
