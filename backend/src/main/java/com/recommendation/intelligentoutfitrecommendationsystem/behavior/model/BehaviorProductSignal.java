package com.recommendation.intelligentoutfitrecommendationsystem.behavior.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户行为与商品事实合并后的轻量信号。
 *
 * 该视图只用于推荐摘要聚合，商品分类和风格标签仍来自 Java 商品事实表。
 */
@Data
public class BehaviorProductSignal {
    private String eventType;
    private Long spuId;
    private Long skuId;
    private String categoryName;
    private String styleTags;
    private LocalDateTime eventTime;
}
