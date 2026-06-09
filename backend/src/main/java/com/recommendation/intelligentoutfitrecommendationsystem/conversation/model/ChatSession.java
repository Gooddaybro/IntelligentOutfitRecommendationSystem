package com.recommendation.intelligentoutfitrecommendationsystem.conversation.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话持久化模型。
 *
 * threadId 是对外会话标识，数据库主键只在 Java 内部和 Mapper 层使用。
 */
@Data
public class ChatSession {
    private Long id;
    private String threadId;
    private Long userId;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastMessageAt;
}
