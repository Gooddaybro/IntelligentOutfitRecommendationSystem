package com.recommendation.intelligentoutfitrecommendationsystem.order;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.IdempotencyKeyConflictException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderIdempotencyMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderIdempotencyRecord;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderOperation;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.IdempotentOrderResult;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderCreationResult;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderIdempotencyCoordinator;
import com.recommendation.intelligentoutfitrecommendationsystem.order.service.OrderIdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyCoordinatorTests {

    @Mock
    private OrderIdempotencyMapper mapper;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    private OrderIdempotencyCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new OrderIdempotencyCoordinator(
                mapper,
                new TransactionTemplate(transactionManager),
                new OrderIdempotencyProperties()
        );
    }

    @Test
    void rejectsInvalidUuidBeforeOpeningTransaction() {
        assertThatThrownBy(() -> coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                "not-a-uuid",
                "a".repeat(64),
                () -> new OrderCreationResult(91L, order("ORD-1")),
                orderId -> order("ORD-1")
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Idempotency-Key must be a valid UUID");

        verifyNoInteractions(mapper, transactionManager);
    }

    @Test
    void firstClaimExecutesOrderActionAndLinksResultOnce() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        AtomicInteger executions = new AtomicInteger();
        doAnswer(invocation -> {
            OrderIdempotencyRecord record = invocation.getArgument(0);
            record.setId(41L);
            return null;
        }).when(mapper).insert(any(OrderIdempotencyRecord.class));
        when(mapper.linkOrder(41L, 91L)).thenReturn(1);

        IdempotentOrderResult result = coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                UUID.randomUUID().toString(),
                "a".repeat(64),
                () -> {
                    executions.incrementAndGet();
                    return new OrderCreationResult(91L, order("ORD-1"));
                },
                orderId -> order("ORD-REPLAY")
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().orderNo()).isEqualTo("ORD-1");
        assertThat(executions).hasValue(1);
        verify(mapper).linkOrder(41L, 91L);
        verify(mapper, never()).deleteExpiredKey(any(), any(), any(), any());
        verify(mapper, never()).findByKey(any(), any(), any());
    }

    @Test
    void duplicateClaimWithSameFingerprintReplaysCommittedOrder() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        String key = UUID.randomUUID().toString();
        doThrow(new DuplicateKeyException("duplicate claim"))
                .when(mapper).insert(any(OrderIdempotencyRecord.class));
        when(mapper.findByKey(10L, "BUY_NOW", key))
                .thenReturn(existingRecord(key, "a".repeat(64), 91L));
        AtomicInteger executions = new AtomicInteger();

        IdempotentOrderResult result = coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                key,
                "a".repeat(64),
                () -> {
                    executions.incrementAndGet();
                    return new OrderCreationResult(92L, order("ORD-NEW"));
                },
                orderId -> order("ORD-1")
        );

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().orderNo()).isEqualTo("ORD-1");
        assertThat(executions).hasValue(0);
    }

    @Test
    void duplicateClaimWithDifferentFingerprintReturnsConflict() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        String key = UUID.randomUUID().toString();
        doThrow(new DuplicateKeyException("duplicate claim"))
                .when(mapper).insert(any(OrderIdempotencyRecord.class));
        when(mapper.findByKey(10L, "BUY_NOW", key))
                .thenReturn(existingRecord(key, "b".repeat(64), 91L));

        assertThatThrownBy(() -> coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                key,
                "a".repeat(64),
                () -> new OrderCreationResult(92L, order("ORD-NEW")),
                orderId -> order("ORD-1")
        ))
                .isInstanceOf(IdempotencyKeyConflictException.class)
                .hasMessage("Idempotency-Key was already used with different request parameters");
    }

    @Test
    void duplicateKeyFromOrderActionIsNotMisclassifiedAsReplay() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        doAnswer(invocation -> {
            OrderIdempotencyRecord record = invocation.getArgument(0);
            record.setId(41L);
            return null;
        }).when(mapper).insert(any(OrderIdempotencyRecord.class));

        assertThatThrownBy(() -> coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                UUID.randomUUID().toString(),
                "a".repeat(64),
                () -> {
                    throw new DuplicateKeyException("duplicate order number");
                },
                orderId -> order("ORD-REPLAY")
        ))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessage("duplicate order number");

        verify(mapper, never()).findByKey(any(), any(), any());
    }

    @Test
    void expiredClaimIsDeletedAfterConflictAndKeyIsClaimedOnceMore() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        String key = UUID.randomUUID().toString();
        OrderIdempotencyRecord expired = existingRecord(key, "a".repeat(64), 91L);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        doThrow(new DuplicateKeyException("expired claim"))
                .doAnswer(invocation -> {
                    OrderIdempotencyRecord record = invocation.getArgument(0);
                    record.setId(42L);
                    return null;
                })
                .when(mapper).insert(any(OrderIdempotencyRecord.class));
        when(mapper.findByKey(10L, "BUY_NOW", key)).thenReturn(expired);
        when(mapper.deleteExpiredKey(any(), any(), any(), any())).thenReturn(1);
        when(mapper.linkOrder(42L, 92L)).thenReturn(1);
        AtomicInteger executions = new AtomicInteger();

        IdempotentOrderResult result = coordinator.execute(
                10L,
                OrderOperation.BUY_NOW,
                key,
                "a".repeat(64),
                () -> {
                    executions.incrementAndGet();
                    return new OrderCreationResult(92L, order("ORD-NEW"));
                },
                orderId -> order("ORD-OLD")
        );

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().orderNo()).isEqualTo("ORD-NEW");
        assertThat(executions).hasValue(1);
        verify(mapper).deleteExpiredKey(eq(10L), eq("BUY_NOW"), eq(key), any());
    }

    private OrderIdempotencyRecord existingRecord(
            String key,
            String fingerprint,
            Long orderId
    ) {
        OrderIdempotencyRecord record = new OrderIdempotencyRecord();
        record.setUserId(10L);
        record.setOperation("BUY_NOW");
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setOrderId(orderId);
        record.setExpiresAt(LocalDateTime.now().plusHours(1));
        return record;
    }

    private OrderResponse order(String orderNo) {
        return new OrderResponse(
                orderNo,
                "UNPAID",
                BigDecimal.TEN,
                List.of(),
                null,
                null,
                null,
                null
        );
    }
}
