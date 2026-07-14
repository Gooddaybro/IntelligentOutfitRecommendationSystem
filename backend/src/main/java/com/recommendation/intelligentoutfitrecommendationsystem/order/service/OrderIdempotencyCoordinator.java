package com.recommendation.intelligentoutfitrecommendationsystem.order.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.IdempotencyKeyConflictException;
import com.recommendation.intelligentoutfitrecommendationsystem.order.dto.OrderResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.order.mapper.OrderIdempotencyMapper;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderIdempotencyRecord;
import com.recommendation.intelligentoutfitrecommendationsystem.order.model.OrderOperation;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Objects;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 协调订单幂等占位与订单创建本地事务。
 *
 * MySQL 唯一约束负责最终并发互斥；协调器确保幂等记录和订单动作共同提交，并把 HTTP
 * 幂等协议与具体的库存、购物车和订单规则隔离。
 */
@Service
public class OrderIdempotencyCoordinator {

    private static final String REUSED_KEY_MESSAGE =
            "Idempotency-Key was already used with different request parameters";

    private final OrderIdempotencyMapper mapper;

    private final TransactionTemplate transactionTemplate;

    private final OrderIdempotencyProperties properties;

    public OrderIdempotencyCoordinator(
            OrderIdempotencyMapper mapper,
            TransactionTemplate transactionTemplate,
            OrderIdempotencyProperties properties
    ) {
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    /**
     * 在一个事务中占用幂等键并执行订单创建动作。
     *
     * @param userId 当前认证用户 ID
     * @param operation 订单创建操作域
     * @param rawKey 客户端购买意图 UUID
     * @param fingerprint 已规范化请求摘要
     * @param createAction 必须在事务中执行的订单创建动作
     * @param replayLoader 按订单主键读取当前订单状态的用户隔离函数
     * @return 首次创建或重放结果
     */
    public IdempotentOrderResult execute(
            Long userId,
            OrderOperation operation,
            String rawKey,
            String fingerprint,
            Supplier<OrderCreationResult> createAction,
            Function<Long, OrderResponse> replayLoader
    ) {
        String key = normalizeKey(rawKey);
        return executeAttempt(
                userId,
                operation,
                key,
                fingerprint,
                createAction,
                replayLoader,
                true
        );
    }

    /**
     * 在一个短事务中删除一批已过期幂等记录。
     *
     * @return 本次实际删除的记录数；没有过期记录时返回零
     */
    public int cleanupExpired() {
        if (properties.getCleanupBatchSize() <= 0) {
            throw new IllegalStateException("order idempotency cleanup batch size must be positive");
        }
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            List<Long> ids = mapper.findExpiredIds(
                    LocalDateTime.now(),
                    properties.getCleanupBatchSize()
            );
            if (ids.isEmpty()) {
                return 0;
            }
            return mapper.deleteByIds(ids);
        }));
    }

    private IdempotentOrderResult executeAttempt(
            Long userId,
            OrderOperation operation,
            String key,
            String fingerprint,
            Supplier<OrderCreationResult> createAction,
            Function<Long, OrderResponse> replayLoader,
            boolean allowExpiredRetry
    ) {
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> executeFirstClaim(
                    userId,
                    operation,
                    key,
                    fingerprint,
                    createAction
            )));
        } catch (IdempotencyClaimConflictException exception) {
            return resolveDuplicate(
                    userId,
                    operation,
                    key,
                    fingerprint,
                    createAction,
                    replayLoader,
                    allowExpiredRetry
            );
        }
    }

    private IdempotentOrderResult executeFirstClaim(
            Long userId,
            OrderOperation operation,
            String key,
            String fingerprint,
            Supplier<OrderCreationResult> createAction
    ) {
        LocalDateTime now = LocalDateTime.now();
        mapper.deleteExpiredKey(userId, operation.name(), key, now);
        OrderIdempotencyRecord record = new OrderIdempotencyRecord();
        record.setUserId(userId);
        record.setOperation(operation.name());
        record.setIdempotencyKey(key);
        record.setRequestFingerprint(fingerprint);
        record.setExpiresAt(now.plus(properties.getRetention()));
        try {
            mapper.insert(record);
        } catch (DuplicateKeyException exception) {
            throw new IdempotencyClaimConflictException(exception);
        }

        OrderCreationResult creation = createAction.get();
        if (creation == null || creation.orderId() == null || creation.order() == null) {
            throw new IllegalStateException("order creation did not return a persistent result");
        }
        if (mapper.linkOrder(record.getId(), creation.orderId()) != 1) {
            throw new IllegalStateException("order idempotency result could not be linked");
        }
        return new IdempotentOrderResult(creation.order(), false);
    }

    private IdempotentOrderResult resolveDuplicate(
            Long userId,
            OrderOperation operation,
            String key,
            String fingerprint,
            Supplier<OrderCreationResult> createAction,
            Function<Long, OrderResponse> replayLoader,
            boolean allowExpiredRetry
    ) {
        OrderIdempotencyRecord existing = mapper.findByKey(userId, operation.name(), key);
        if (existing == null) {
            throw new IllegalStateException("conflicting order idempotency record was not found");
        }
        if (!existing.getExpiresAt().isAfter(LocalDateTime.now())) {
            if (allowExpiredRetry) {
                return executeAttempt(
                        userId,
                        operation,
                        key,
                        fingerprint,
                        createAction,
                        replayLoader,
                        false
                );
            }
            throw new IllegalStateException("expired order idempotency record could not be replaced");
        }
        if (!Objects.equals(existing.getRequestFingerprint(), fingerprint)) {
            throw new IdempotencyKeyConflictException(REUSED_KEY_MESSAGE);
        }
        if (existing.getOrderId() == null) {
            throw new IllegalStateException("order idempotency record is not linked to an order");
        }
        OrderResponse replay = replayLoader.apply(existing.getOrderId());
        if (replay == null) {
            throw new IllegalStateException("idempotent order result was not found");
        }
        return new IdempotentOrderResult(replay, true);
    }

    private String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new BadRequestException("Idempotency-Key must be a valid UUID");
        }
        String candidate = rawKey.trim();
        try {
            String normalized = UUID.fromString(candidate).toString();
            if (!normalized.equalsIgnoreCase(candidate)) {
                throw new IllegalArgumentException("non-canonical UUID");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Idempotency-Key must be a valid UUID");
        }
    }

    private static final class IdempotencyClaimConflictException extends RuntimeException {

        private IdempotencyClaimConflictException(DuplicateKeyException cause) {
            super(cause);
        }
    }
}
