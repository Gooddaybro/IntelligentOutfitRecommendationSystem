package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Java 完成最终事实校验、消息保存和推荐归因后发送的 Pro 终结事件。
 *
 * <p>商品卡片字段全部来自 Java 当前商品事实；Python 只能提供本轮已登记的引用和推荐理由。</p>
 *
 * @param threadId 当前用户会话
 * @param runId 本轮服务端运行标识
 * @param agentMode 固定为 pro
 * @param answer 已通过 Java 校验并保存的回答
 * @param recommendedSpuIds 已核验的商品 SPU 标识
 * @param recommendedItems 已核验的可展示 SKU 事实
 * @param recommendationStatus Java 最终推荐状态
 * @param requirements 经安全过滤后的需求状态
 * @param recommendationId 已持久化的推荐归因快照标识
 */
public record ProDoneEvent(
        @JsonProperty("thread_id") String threadId,
        @JsonProperty("run_id") String runId,
        @JsonProperty("agent_mode") String agentMode,
        String answer,
        @JsonProperty("recommended_spu_ids") List<Long> recommendedSpuIds,
        @JsonProperty("recommended_items") List<RecommendedItem> recommendedItems,
        @JsonProperty("recommendation_status") String recommendationStatus,
        List<Object> requirements,
        @JsonProperty("recommendation_id") String recommendationId
) {
    public ProDoneEvent {
        recommendedSpuIds = recommendedSpuIds == null ? List.of() : List.copyOf(recommendedSpuIds);
        recommendedItems = recommendedItems == null ? List.of() : List.copyOf(recommendedItems);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    /**
     * Java 当前商品事实和 Python 对该商品的安全推荐理由。
     *
     * @param spuId 商品 SPU
     * @param skuId 可售 SKU
     * @param name 商品名称
     * @param salePrice 当前销售价字符串，保持 CNY 精度
     * @param mainImageUrl 商品主图
     * @param color 当前 SKU 颜色
     * @param size 当前 SKU 尺码
     * @param availableStock 当前可用库存
     * @param reason 本轮安全推荐理由
     * @param sizeAdvice 可选的非承诺尺码提示
     * @param basis 尺码提示依据
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RecommendedItem(
            @JsonProperty("spu_id") Long spuId,
            @JsonProperty("sku_id") Long skuId,
            String name,
            @JsonProperty("sale_price") String salePrice,
            @JsonProperty("main_image_url") String mainImageUrl,
            String color,
            String size,
            @JsonProperty("available_stock") Integer availableStock,
            String reason,
            @JsonProperty("size_advice") String sizeAdvice,
            String basis
    ) {
    }
}
