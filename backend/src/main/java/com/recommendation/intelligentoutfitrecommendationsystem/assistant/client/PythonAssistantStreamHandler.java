package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;

/**
 * Python 流式回答事件的回调边界。
 *
 * HTTP 客户端通过该接口把 token/done/error 交给 Service 层，避免业务编排依赖 SSE 解析细节。
 */
public interface PythonAssistantStreamHandler {

    /**
     * 转交 Python 生成的自然语言增量内容。
     *
     * @param content 本次生成的文本增量
     */
    void onToken(String content);

    /**
     * 转交 Python 生成完成后的结构化响应。
     *
     * @param response Python 返回的完整回答和推荐引用
     */
    void onDone(PythonChatResponse response);

    /**
     * 转交 Python 流式生成失败信息。
     *
     * @param code 稳定错误码，用于前端和日志分类
     * @param message 可展示或可记录的错误描述
     */
    void onError(String code, String message);
}
