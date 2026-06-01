package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Python `/chat` 响应中被选中的商品引用。
 *
 * Python 只返回 Java 商品库里的引用和推荐理由；商品事实仍由 Java 商品模块维护。
 */
public record PythonProductRef(
        @JsonProperty("spu_id") Long spuId,
        @JsonProperty("sku_id") Long skuId,
        @JsonProperty("reason") String reason,
        @JsonProperty("rank_score") BigDecimal rankScore
) {
}
