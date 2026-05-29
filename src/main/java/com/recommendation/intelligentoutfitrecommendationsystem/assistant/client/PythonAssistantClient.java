package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;

/**
 * Java 调 Python AI 服务的适配边界。
 *
 * Service 层只依赖这个接口，测试和后续 SSE/MQ 形态可以替换实现而不改业务编排。
 */
public interface PythonAssistantClient {
    PythonChatResponse chat(PythonChatRequest request);
}
