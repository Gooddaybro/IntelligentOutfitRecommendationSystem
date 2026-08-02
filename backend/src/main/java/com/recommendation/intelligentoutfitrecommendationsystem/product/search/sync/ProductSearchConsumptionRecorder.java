package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheVersionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 在同一个数据库事务中推进搜索缓存版本，并记录消费端幂等凭证。
 */
@Service
public class ProductSearchConsumptionRecorder {
    static final String CONSUMER_NAME = "product-search-worker-v1";

    private final ProductSearchCacheVersionService versionService;
    private final ProductSearchInboxMapper inboxMapper;
    private final Clock clock;

    public ProductSearchConsumptionRecorder(
            ProductSearchCacheVersionService versionService,
            ProductSearchInboxMapper inboxMapper,
            Clock clock) {
        this.versionService = versionService;
        this.inboxMapper = inboxMapper;
        this.clock = clock;
    }

    /**
     * 记录一次成功投影；任意一步失败都会回滚，避免版本和 Inbox 状态分离。
     */
    @Transactional
    public void record(ProductSearchSyncMessage message) {
        versionService.incrementVersion();
        inboxMapper.insert(
                CONSUMER_NAME,
                message.eventId(),
                message.spuId(),
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }
}
