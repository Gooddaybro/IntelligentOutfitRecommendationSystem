package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Java SSE 流完成时返回给前端的最终结果。
 *
 * 只有收到 Python done 后才发送该事件，此时 Java 已具备保存完整助手消息和稳定推荐引用的条件。
 *
 * @param threadId Java 会话标识
 * @param answer 完整助手回答
 * @param recommendedSpuIds Python 从 Java 候选池中选出的 SPU 引用
 * @param recommendedItems Python 对候选商品的用户可见推荐理由
 * @param candidatesCount 本轮 Java 提供给 Python 的候选商品数量
 * @param intent Python 识别出的用户意图
 * @param resolvedIntent Java 统一解析出的筛选意图
 * @param recommendationStatus Java 核验后的最终推荐状态
 * @param recommendationId Java 已持久化的推荐快照 ID，供后续交互和交易归因
 */
public record AssistantStreamDoneEvent(
        @JsonProperty("thread_id") String threadId,
        String answer,
        @JsonProperty("recommended_spu_ids") List<Long> recommendedSpuIds,
        @JsonProperty("recommended_items") List<AssistantRecommendationItem> recommendedItems,
        @JsonProperty("candidates_count") int candidatesCount,
        String intent,
        @JsonProperty("resolved_intent") DemandIntent resolvedIntent,
        @JsonProperty("recommendation_status") RecommendationStatus recommendationStatus,
        @JsonProperty("recommendation_id") String recommendationId,
        RecommendationDiagnostics diagnostics
) {
    public AssistantStreamDoneEvent(
            String threadId,
            String answer,
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            int candidatesCount,
            String intent,
            DemandIntent resolvedIntent,
            RecommendationStatus recommendationStatus
    ) {
        this(threadId, answer, recommendedSpuIds, recommendedItems, candidatesCount, intent,
                resolvedIntent, recommendationStatus, null, null);
    }

    public AssistantStreamDoneEvent(
            String threadId,
            String answer,
            List<Long> recommendedSpuIds,
            List<AssistantRecommendationItem> recommendedItems,
            int candidatesCount,
            String intent
    ) {
        this(threadId, answer, recommendedSpuIds, recommendedItems, candidatesCount, intent, null,
                recommendedItems == null || recommendedItems.isEmpty()
                        ? RecommendationStatus.BROWSE_FALLBACK : RecommendationStatus.STRONG_MATCH, null, null);
    }
}
