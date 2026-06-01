package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Python `/chat` 历史消息项，把 Java 按行存储的会话历史压缩为一轮用户问题和助手回答。
 */
public record PythonChatHistoryItem(
        @JsonProperty("user_query") String userQuery,
        @JsonProperty("assistant_answer") String assistantAnswer
) {
}
