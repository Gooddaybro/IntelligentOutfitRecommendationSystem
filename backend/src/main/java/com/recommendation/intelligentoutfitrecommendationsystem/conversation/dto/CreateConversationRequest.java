package com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto;

import jakarta.validation.constraints.Size;

/**
 * 创建 AI 会话的请求。
 *
 * title 可以为空；assistant-service 会在首次问答时用用户首问生成一个临时标题。
 */
public record CreateConversationRequest(
        @Size(max = 128) String title
) {
}
