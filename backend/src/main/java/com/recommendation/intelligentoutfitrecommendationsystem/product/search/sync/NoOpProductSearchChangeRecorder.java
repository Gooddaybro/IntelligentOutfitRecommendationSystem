package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

/**
 * 增量同步关闭时的空实现，避免业务服务感知功能开关。
 */
public class NoOpProductSearchChangeRecorder implements ProductSearchChangeRecorder {
    @Override
    public void record(Long spuId) {
        // 功能关闭时刻意不产生 Outbox；重新开启后通过全量重建恢复搜索副本。
    }
}
