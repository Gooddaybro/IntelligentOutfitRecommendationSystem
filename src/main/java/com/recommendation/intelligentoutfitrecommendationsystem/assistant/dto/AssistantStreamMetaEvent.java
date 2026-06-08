package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java SSE 流开始时返回给前端的元信息。
 *
 * 前端用该事件确认 Java 已完成会话归属校验和用户消息落库，后续 token 属于同一轮请求。
 *
 * @param requestId 当前 AI 请求链路标识
 * @param threadId Java 会话标识
 */
public record AssistantStreamMetaEvent(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("thread_id") String threadId
) {
}
