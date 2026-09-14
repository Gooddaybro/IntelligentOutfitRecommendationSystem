package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Java-owned v2 transport, separate from Lite's intent and initial-candidate contract.
 * The run credential is transport-only and is redacted from diagnostic string output.
 */
public record ProPythonChatRequest(
        @JsonProperty("contract_version") String contractVersion,
        @JsonProperty("agent_mode") String agentMode,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("run_id") String runId,
        @JsonProperty("thread_id") String threadId,
        String query,
        @JsonProperty("chat_history") List<PythonChatHistoryItem> chatHistory,
        @JsonProperty("user_context") Map<String, Object> userContext,
        @JsonProperty("explicit_filters") Map<String, String> explicitFilters,
        @JsonProperty("current_product_refs") List<Map<String, Long>> currentProductRefs,
        @JsonProperty("tool_run_token") String toolRunToken
) {
    @Override
    public String toString() {
        return "ProPythonChatRequest[requestId=" + requestId + ", runId=" + runId + ", content=REDACTED]";
    }
}
