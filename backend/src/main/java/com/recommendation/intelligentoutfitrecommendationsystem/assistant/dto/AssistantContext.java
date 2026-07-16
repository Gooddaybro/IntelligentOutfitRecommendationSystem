package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.behavior.dto.BehaviorSummaryResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;

import java.util.List;

/**
 * Java 内部组装出的 AI 上下文快照。
 *
 * 该对象不直接暴露给前端，用于把用户画像、历史消息和候选商品传给 Python。
 */
public record AssistantContext(
        UserProfileResponse profile,
        UserBodyDataResponse bodyData,
        UserPreferencesResponse preferences,
        BehaviorSummaryResponse behaviorSummary,
        List<MessageResponse> chatHistory,
        List<RecommendationCandidate> candidates,
        DemandIntent demandIntent,
        String clarificationQuestion
) {
    public AssistantContext {
        demandIntent = demandIntent == null ? DemandIntent.empty(null) : demandIntent;
    }

    public AssistantContext(
            UserProfileResponse profile,
            UserBodyDataResponse bodyData,
            UserPreferencesResponse preferences,
            BehaviorSummaryResponse behaviorSummary,
            List<MessageResponse> chatHistory,
            List<RecommendationCandidate> candidates,
            DemandIntent demandIntent
    ) {
        this(profile, bodyData, preferences, behaviorSummary, chatHistory, candidates, demandIntent, null);
    }

    public AssistantContext(
            UserProfileResponse profile,
            UserBodyDataResponse bodyData,
            UserPreferencesResponse preferences,
            BehaviorSummaryResponse behaviorSummary,
            List<MessageResponse> chatHistory,
            List<RecommendationCandidate> candidates
    ) {
        this(profile, bodyData, preferences, behaviorSummary, chatHistory, candidates, DemandIntent.empty(null), null);
    }

    public AssistantContext(
            UserProfileResponse profile,
            UserBodyDataResponse bodyData,
            UserPreferencesResponse preferences,
            List<MessageResponse> chatHistory,
            List<RecommendationCandidate> candidates
    ) {
        this(profile, bodyData, preferences, null, chatHistory, candidates, DemandIntent.empty(null), null);
    }
}
