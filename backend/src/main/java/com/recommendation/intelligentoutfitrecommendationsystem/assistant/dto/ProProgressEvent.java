package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java 对外转发的 Pro 工具进度。
 *
 * <p>进度只包含服务端选定的短消息和本轮运行标识，不暴露工具参数、原始结果或模型推理。</p>
 *
 * @param runId 本轮服务端生成的运行标识
 * @param sequence Python 在本轮运行内分配的递增序号
 * @param tool 工具稳定名称
 * @param stage 工具开始或完成阶段
 * @param message 可直接用于前端状态展示的短消息
 */
public record ProProgressEvent(
        @JsonProperty("run_id") String runId,
        long sequence,
        String tool,
        String stage,
        String message
) {
}
