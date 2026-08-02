package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

/**
 * 在商品事务中记录需要重新投影的 SPU。
 */
public interface ProductSearchChangeRecorder {
    void record(Long spuId);
}
