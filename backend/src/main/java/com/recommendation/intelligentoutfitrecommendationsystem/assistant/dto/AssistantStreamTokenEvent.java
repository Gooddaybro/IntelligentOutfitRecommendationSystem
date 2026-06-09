package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

/**
 * Java 转发给前端的 AI 文本增量。
 *
 * 该事件只承载自然语言片段，不包含商品事实数据，避免前端根据半截内容做业务决策。
 *
 * @param content 本次生成的文本增量
 */
public record AssistantStreamTokenEvent(String content) {
}
