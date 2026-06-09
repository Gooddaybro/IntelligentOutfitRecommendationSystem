package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Java SSE 流异常结束时返回给前端的错误负载。
 *
 * message 面向前端状态展示，不能包含 Python 内部 URL、堆栈、密钥或其他敏感实现细节。
 *
 * @param code 稳定错误码
 * @param message 可展示的错误描述
 */
public record AssistantStreamErrorEvent(String code, String message) {
}
