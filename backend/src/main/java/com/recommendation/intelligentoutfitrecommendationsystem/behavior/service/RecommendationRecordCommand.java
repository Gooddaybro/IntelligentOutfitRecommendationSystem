package com.recommendation.intelligentoutfitrecommendationsystem.behavior.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Assistant 模块提交推荐快照的跨模块命令。
 *
 * 该命令只携带稳定标识和商品引用，不把 Python DTO、商品模型或完整 Prompt 泄漏到 Behavior 模块。
 */
public record RecommendationRecordCommand(
        Long userId,
        String requestId,
        String threadId,
        String mode,
        List<Item> candidates,
        List<Item> selectedItems
) {

    /**
     * 候选或最终选择的最小商品引用；候选项的 rankScore 可以为空。
     */
    public record Item(Long spuId, Long skuId, BigDecimal rankScore) {
    }
}
