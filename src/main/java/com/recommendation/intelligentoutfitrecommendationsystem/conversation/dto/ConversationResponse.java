package com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto;

import java.time.LocalDateTime;

/**
 * 会话列表返回给前端的摘要。
 *
 * 只暴露 threadId，不暴露数据库主键，降低前端误用内部 ID 的风险。
 */
public record ConversationResponse(
        String threadId,
        String title,
        String status,
        LocalDateTime createdAt,
        LocalDateTime lastMessageAt
) {
}
