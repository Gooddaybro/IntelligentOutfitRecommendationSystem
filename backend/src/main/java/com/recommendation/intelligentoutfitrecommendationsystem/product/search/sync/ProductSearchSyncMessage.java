package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import java.time.Instant;

/**
 * 商品重新投影请求；只携带定位信息，消费时始终读取 MySQL 最新事实。
 */
public record ProductSearchSyncMessage(
        String eventId,
        Long spuId,
        String eventType,
        Instant occurredAt,
        int schemaVersion
) {
    public static final String EVENT_TYPE = "PRODUCT_SEARCH_REINDEX_REQUESTED";
    public static final int SCHEMA_VERSION = 1;
}
