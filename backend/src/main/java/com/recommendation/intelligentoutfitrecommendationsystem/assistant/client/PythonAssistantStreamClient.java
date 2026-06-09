package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;

/**
 * Java 调 Python `/chat/stream` 的客户端边界。
 *
 * Service 层依赖该接口处理流式导购，不直接持有 HTTP、SSE 或 JSON 帧解析细节。
 */
public interface PythonAssistantStreamClient {

    /**
     * 发送 Java 装配后的 Python 请求，并把 Python SSE 事件同步回调给调用方。
     *
     * @param request Java 生成的 Python 流式问答请求
     * @param handler Python 流式事件回调
     */
    void streamChat(PythonChatRequest request, PythonAssistantStreamHandler handler);
}
