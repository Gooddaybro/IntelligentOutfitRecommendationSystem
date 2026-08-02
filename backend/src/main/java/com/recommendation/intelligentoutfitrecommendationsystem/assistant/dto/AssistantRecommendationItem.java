package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 推荐结果中可展示给前端的商品解释。
 *
 * 该 DTO 只承载 Python 对 Java 候选商品的排序说明；商品名称、价格和库存仍由 Java 商品接口返回。
 *
 * @param spuId Java 商品 SPU ID
 * @param skuId Java 可售 SKU ID
 * @param reason 用户可见的推荐理由
 * @param rankScore Python 返回的排序分，仅用于展示或调试，不参与交易决策
 * @param matchedDimensions Java 已核验的结构化匹配证据
 * @param outfitRole 经 Java 商品分类校验后的穿搭角色
 */
public record AssistantRecommendationItem(
        Long spuId,
        Long skuId,
        String reason,
        BigDecimal rankScore,
        List<MatchedDimension> matchedDimensions,
        String outfitRole
) {
    public AssistantRecommendationItem {
        matchedDimensions = matchedDimensions == null ? List.of() : List.copyOf(matchedDimensions);
    }

    /** Creates a legacy weak item without structured evidence or an outfit role. */
    public AssistantRecommendationItem(Long spuId, Long skuId, String reason, BigDecimal rankScore) {
        this(spuId, skuId, reason, rankScore, List.of(), null);
    }
}
