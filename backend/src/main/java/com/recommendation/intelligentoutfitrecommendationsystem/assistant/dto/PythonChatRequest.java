package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Java 调用 Python `/chat` 的跨服务请求契约。
 *
 * Java 内部保持驼峰命名；发给 Python 的 JSON 字段通过 JsonProperty 固定为 Pydantic 期望的 snake_case。
 */
public record PythonChatRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("thread_id") String threadId,
        @JsonProperty("query") String query,
        @JsonProperty("chat_history") List<PythonChatHistoryItem> chatHistory,
        @JsonProperty("user_context") PythonUserContext userContext,
        @JsonProperty("candidates") List<PythonProductCandidate> candidates,
        @JsonProperty("debug") Boolean debug
) {
}
