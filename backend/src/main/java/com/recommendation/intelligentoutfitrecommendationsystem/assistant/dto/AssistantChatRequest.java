package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 前端发起 AI 导购问答的请求。
 *
 * 可选筛选字段会先在 Java 商品库中过滤候选池，再交给 Python 生成自然语言推荐。
 */
public record AssistantChatRequest(
        @Size(max = 64) String threadId,
        @NotBlank @Size(max = 2000) String message,
        String category,
        String style,
        String season,
        String material,
        String fit,
        String gender,
        /**
         * 预算上限，对应商品销售价的数据库货币单位。
         */
        @PositiveOrZero Integer budgetMax
) {
    public AssistantChatRequest(
            String threadId,
            String message,
            String category,
            String style,
            String season,
            String material,
            String fit,
            Integer budgetMax
    ) {
        this(threadId, message, category, style, season, material, fit, null, budgetMax);
    }
}
