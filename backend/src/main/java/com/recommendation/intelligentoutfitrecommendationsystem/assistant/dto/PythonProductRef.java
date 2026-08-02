package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Python `/chat` 响应中被选中的商品引用。
 *
 * Python 只返回 Java 商品库里的引用和推荐理由；商品事实仍由 Java 商品模块维护。
 */
public record PythonProductRef(
        @JsonProperty("spu_id") Long spuId,
        @JsonProperty("sku_id") Long skuId,
        @JsonProperty("reason") String reason,
        @JsonProperty("rank_score") BigDecimal rankScore,
        @JsonProperty("matched_dimensions") List<MatchedDimension> matchedDimensions,
        @JsonProperty("outfit_role") String outfitRole
) {
    public PythonProductRef {
        matchedDimensions = matchedDimensions == null ? List.of() : List.copyOf(matchedDimensions);
    }

    /** Creates a v2 response reference without an outfit role. */
    public PythonProductRef(
            Long spuId,
            Long skuId,
            String reason,
            BigDecimal rankScore,
            List<MatchedDimension> matchedDimensions
    ) {
        this(spuId, skuId, reason, rankScore, matchedDimensions, null);
    }

    /** Creates a legacy response reference without v2 match evidence. */
    public PythonProductRef(Long spuId, Long skuId, String reason, BigDecimal rankScore) {
        this(spuId, skuId, reason, rankScore, List.of(), null);
    }
}
