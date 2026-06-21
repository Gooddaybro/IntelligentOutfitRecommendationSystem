package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;

/**
 * AI 推荐结果中可展示给前端的商品解释。
 *
 * 该 DTO 只承载 Python 对 Java 候选商品的排序说明；商品名称、价格和库存仍由 Java 商品接口返回。
 *
 * @param spuId Java 商品 SPU ID
 * @param skuId Java 可售 SKU ID
 * @param reason 用户可见的推荐理由
 * @param rankScore Python 返回的排序分，仅用于展示或调试，不参与交易决策
 */
public record AssistantRecommendationItem(
        Long spuId,
        Long skuId,
        String reason,
        BigDecimal rankScore
) {
}
