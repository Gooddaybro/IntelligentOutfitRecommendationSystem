package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.util.List;

/**
 * Python `/chat` 第一版响应契约。
 *
 * answer 是自然语言回答，recommendedSpuIds 是可被 Java 商品接口继续解析的商品引用。
 */
public record PythonChatResponse(
        String answer,
        List<Long> recommendedSpuIds
) {
}
