package com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheVersionService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

class ProductSearchConsumptionRecorderTests {
    private final ProductSearchCacheVersionService versionService = mock(ProductSearchCacheVersionService.class);
    private final ProductSearchInboxMapper inboxMapper = mock(ProductSearchInboxMapper.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-20T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void advancesCacheVersionBeforeRecordingInbox() {
        ProductSearchConsumptionRecorder recorder =
                new ProductSearchConsumptionRecorder(versionService, inboxMapper, clock);
        ProductSearchSyncMessage message = new ProductSearchSyncMessage(
                "event-1", 1001L, ProductSearchSyncMessage.EVENT_TYPE,
                Instant.parse("2026-07-20T09:59:00Z"), ProductSearchSyncMessage.SCHEMA_VERSION);

        recorder.record(message);

        InOrder order = inOrder(versionService, inboxMapper);
        order.verify(versionService).incrementVersion();
        order.verify(inboxMapper).insert(
                ProductSearchConsumptionRecorder.CONSUMER_NAME,
                "event-1",
                1001L,
                LocalDateTime.of(2026, 7, 20, 10, 0));
    }
}
