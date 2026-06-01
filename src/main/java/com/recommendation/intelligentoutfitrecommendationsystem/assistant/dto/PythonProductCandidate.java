package com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * Python `/chat` 候选商品契约，隔离 Java 商品查询模型和 Python 推荐输入字段。
 */
public record PythonProductCandidate(
        @JsonProperty("spu_id") Long spuId,
        @JsonProperty("sku_id") Long skuId,
        @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("sale_price") BigDecimal salePrice,
        @JsonProperty("stock_status") String stockStatus,
        @JsonProperty("color") String color,
        @JsonProperty("size") String size,
        @JsonProperty("brand") String brand,
        @JsonProperty("material") String material,
        @JsonProperty("fit_type") String fitType,
        @JsonProperty("season") List<String> season,
        @JsonProperty("style_tags") List<String> styleTags,
        @JsonProperty("main_image_url") String mainImageUrl
) {
}
