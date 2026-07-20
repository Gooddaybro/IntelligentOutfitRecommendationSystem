package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 补偿全量快照读取期间发生的商品变化，关闭“快照读取完成到别名切换”之间的数据窗口。
 */
@Component
@ConditionalOnProperty(prefix = "app.product-search-sync", name = "enabled", havingValue = "true")
public class ProductSearchRebuildCompensator {
    private final ProductSearchOutboxMapper outboxMapper;
    private final ProductSearchIncrementalProjector projector;

    public ProductSearchRebuildCompensator(
            ProductSearchOutboxMapper outboxMapper,
            ProductSearchIncrementalProjector projector) {
        this.outboxMapper = outboxMapper;
        this.projector = projector;
    }

    /** 在读取全量 MySQL 快照之前记录 W0。 */
    public long captureWatermark() {
        Long value = outboxMapper.findMaxId();
        return value == null ? 0 : value;
    }

    /**
     * 别名切换后记录 W1，并按 SPU 去重重放 (W0, W1] 内的变化。
     */
    public void compensateAfter(long startWatermark) {
        long endWatermark = captureWatermark();
        if (endWatermark <= startWatermark) {
            return;
        }
        List<Long> changedSpuIds = outboxMapper.findDistinctSpuIdsInRange(startWatermark, endWatermark);
        changedSpuIds.forEach(projector::project);
    }
}
