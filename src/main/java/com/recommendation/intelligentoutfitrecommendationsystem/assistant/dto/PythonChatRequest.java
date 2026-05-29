package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto.MessageResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserBodyDataResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserPreferencesResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.user.dto.UserProfileResponse;

import java.util.List;

/**
 * Java 调 Python `/chat` 的跨服务契约。
 *
 * 商品、库存、用户画像都来自 Java 主系统，Python 根据这些事实生成回答，不再依赖本地 product_catalog.json。
 */
public record PythonChatRequest(
        String threadId,
        Long userId,
        String message,
        UserProfileResponse profile,
        UserBodyDataResponse bodyData,
        UserPreferencesResponse preferences,
        List<MessageResponse> chatHistory,
        List<RecommendationCandidate> candidates
) {
}
