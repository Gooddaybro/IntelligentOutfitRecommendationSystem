package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderIdempotencyMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderIdempotencyRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class OrderIdempotencyMapperTests {

    @Autowired
    private OrderIdempotencyMapper mapper;

    @Test
    void storesLinksAndFindsOrderIdempotencyClaim() {
        OrderIdempotencyRecord record = record("CART_CHECKOUT", UUID.randomUUID().toString());

        mapper.insert(record);

        assertThat(record.getId()).isPositive();
        assertThat(mapper.linkOrder(record.getId(), 9201L)).isOne();
        OrderIdempotencyRecord stored = mapper.findByKey(
                9001L,
                "CART_CHECKOUT",
                record.getIdempotencyKey()
        );
        assertThat(stored.getRequestFingerprint()).isEqualTo("a".repeat(64));
        assertThat(stored.getOrderId()).isEqualTo(9201L);
    }

    @Test
    void rejectsDuplicateUserOperationAndKey() {
        String key = UUID.randomUUID().toString();
        mapper.insert(record("BUY_NOW", key));

        assertThatThrownBy(() -> mapper.insert(record("BUY_NOW", key)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void selectsAndDeletesExpiredRecordsInBoundedBatches() {
        LocalDateTime now = LocalDateTime.now();
        OrderIdempotencyRecord firstExpired = record("BUY_NOW", UUID.randomUUID().toString());
        firstExpired.setExpiresAt(now.minusHours(2));
        OrderIdempotencyRecord secondExpired = record("BUY_NOW", UUID.randomUUID().toString());
        secondExpired.setExpiresAt(now.minusHours(1));
        OrderIdempotencyRecord active = record("BUY_NOW", UUID.randomUUID().toString());
        active.setExpiresAt(now.plusHours(1));
        mapper.insert(firstExpired);
        mapper.insert(secondExpired);
        mapper.insert(active);

        var expiredIds = mapper.findExpiredIds(now, 1);

        assertThat(expiredIds).containsExactly(firstExpired.getId());
        assertThat(mapper.deleteByIds(expiredIds)).isOne();
        assertThat(mapper.findByKey(9001L, "BUY_NOW", active.getIdempotencyKey())).isNotNull();
        assertThat(mapper.findExpiredIds(now, 10)).containsExactly(secondExpired.getId());
    }

    private OrderIdempotencyRecord record(String operation, String key) {
        OrderIdempotencyRecord record = new OrderIdempotencyRecord();
        record.setUserId(9001L);
        record.setOperation(operation);
        record.setIdempotencyKey(key);
        record.setRequestFingerprint("a".repeat(64));
        record.setExpiresAt(LocalDateTime.now().plusHours(24));
        return record;
    }
}
