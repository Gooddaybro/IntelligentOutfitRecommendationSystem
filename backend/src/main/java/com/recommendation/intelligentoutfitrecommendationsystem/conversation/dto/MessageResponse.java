package com.recommendation.intelligentoutfitrecommendationsystem.conversation.dto;

import java.time.LocalDateTime;

/**
 * 会话消息返回结构。
 *
 * requestId 用于把一次 AI 问答的前端请求、服务端日志和消息历史关联起来。
 */
public record MessageResponse(
        String role,
        String content,
        String messageStatus,
        String requestId,
        LocalDateTime createdAt
) {
}
