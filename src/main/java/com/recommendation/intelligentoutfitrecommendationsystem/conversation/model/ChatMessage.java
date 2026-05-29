package com.recommendation.intelligentoutfitrecommendationsystem.conversation.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息持久化模型。
 *
 * user 和 assistant 消息共用一张表，便于按时间线还原 Python 调用上下文。
 */
@Data
public class ChatMessage {
    private Long id;
    private Long sessionId;
    private Long userId;
    private String role;
    private String content;
    private String messageStatus;
    private String requestId;
    private LocalDateTime createdAt;
}
