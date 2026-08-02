package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.search.cache.ProductSearchCacheVersionService;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchConsumptionRecorder;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchInboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxEvent;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchOutboxMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.product.search.sync.ProductSearchSyncMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
class ProductSearchSyncPersistenceTests {
    @Autowired
    private ProductSearchOutboxMapper outboxMapper;
    @Autowired
    private ProductSearchInboxMapper inboxMapper;
    @Autowired
    private ProductSearchConsumptionRecorder consumptionRecorder;
    @Autowired
    private ProductSearchCacheVersionService versionService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM product_search_inbox");
        jdbcTemplate.update("DELETE FROM product_search_outbox");
    }

    @Test
    void claimsPublishableEventsAndProvidesRebuildWatermarks() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        ProductSearchOutboxEvent first = insert("event-1", 1001L, now.minusSeconds(1));
        insert("event-2", 1001L, now.minusSeconds(1));
        ProductSearchOutboxEvent last = insert("event-3", 1002L, now.plusMinutes(1));

        assertThat(outboxMapper.findPublishable(now, 20))
                .extracting(ProductSearchOutboxEvent::getEventId)
                .containsExactly("event-1", "event-2");
        assertThat(outboxMapper.claim("event-1", "relay-a", now, now.plusSeconds(30))).isEqualTo(1);
        assertThat(outboxMapper.claim("event-1", "relay-b", now, now.plusSeconds(30))).isZero();
        assertThat(outboxMapper.findMaxId()).isEqualTo(last.getId());
        assertThat(outboxMapper.findDistinctSpuIdsInRange(first.getId() - 1, last.getId()))
                .containsExactly(1001L, 1002L);
    }

    @Test
    void inboxRejectsDuplicateConsumerAndEventPair() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        inboxMapper.insert("worker-v1", "event-1", 1001L, now);

        assertThat(inboxMapper.exists("worker-v1", "event-1")).isTrue();
        assertThatThrownBy(() -> inboxMapper.insert("worker-v1", "event-1", 1001L, now))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void duplicateInboxRollsBackCacheVersionAdvance() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 20, 10, 0);
        inboxMapper.insert("product-search-worker-v1", "event-rollback", 1001L, now);
        long versionBefore = versionService.currentVersion();
        ProductSearchSyncMessage message = new ProductSearchSyncMessage(
                "event-rollback", 1001L, ProductSearchSyncMessage.EVENT_TYPE,
                Instant.parse("2026-07-20T10:00:00Z"), ProductSearchSyncMessage.SCHEMA_VERSION);

        assertThatThrownBy(() -> consumptionRecorder.record(message))
                .isInstanceOf(DuplicateKeyException.class);
        assertThat(versionService.currentVersion()).isEqualTo(versionBefore);
    }

    private ProductSearchOutboxEvent insert(String eventId, Long spuId, LocalDateTime availableAt) {
        ProductSearchOutboxEvent event = new ProductSearchOutboxEvent();
        event.setEventId(eventId);
        event.setSpuId(spuId);
        event.setEventType("PRODUCT_SEARCH_REINDEX_REQUESTED");
        event.setSchemaVersion(1);
        event.setPayload("{}");
        event.setStatus("NEW");
        event.setAvailableAt(availableAt);
        outboxMapper.insert(event);
        return event;
    }
}
