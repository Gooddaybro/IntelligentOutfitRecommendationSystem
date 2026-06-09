package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

/**
 * Python SSE 流中的单帧事件。
 *
 * 该类型只表达 SSE 边界解析结果，不解析 data 内部 JSON，避免 HTTP 帧处理和业务 DTO 反序列化耦合。
 *
 * @param event Python 发送的 SSE 事件名，例如 token、done 或 error
 * @param data 事件负载的原始 JSON 字符串
 */
public record PythonSseEvent(String event, String data) {
}
