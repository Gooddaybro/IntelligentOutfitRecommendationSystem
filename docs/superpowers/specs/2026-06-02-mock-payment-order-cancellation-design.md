# Mock Payment and Order Cancellation MVP Design

## Purpose

This design defines the next Java backend phase after the existing cart and order MVPs. The current order module can create an `UNPAID` order from selected cart SKU items, write immutable item snapshots, lock inventory by moving stock from `available_stock` to `locked_stock`, and remove purchased SKU items from the user's cart.

The next phase should close the first transaction loop without adding real payment providers, SSE, MQ, shipment, refund, or after-sales flows. It will add mock payment, payment records, manual cancellation, timeout closing, and locked-stock confirmation or release.

## Current State

Implemented:

- `POST /api/orders` creates `UNPAID` orders from the current user's cart only.
- `GET /api/orders` lists the current user's orders.
- `GET /api/orders/{orderNo}` reads the current user's order detail.
- `sales_order` stores `order_no`, `user_id`, `total_amount`, `status`, `paid_at`, timestamps.
- `order_item` stores the purchase-time product, SKU, price, quantity, and image snapshot.
- `InventoryMapper#lockStock` atomically moves stock from `available_stock` to `locked_stock`.

Deferred before this design:

- Mock payment.
- Payment record persistence.
- Converting `locked_stock` to `sold_stock`.
- Releasing `locked_stock` for cancelled or timed-out orders.
- Any real payment provider.

## Scope

This phase will implement:

- A `payment` table for mock payment records and idempotency support.
- `POST /api/payments/mock-pay` for authenticated users.
- Payment success logic that moves order item quantities from `locked_stock` to `sold_stock`.
- Order status transition from `UNPAID` to `PAID`.
- `POST /api/orders/{orderNo}/cancel` for authenticated users cancelling their own unpaid orders.
- Cancellation logic that moves order item quantities from `locked_stock` back to `available_stock`.
- Order status transition from `UNPAID` to `CANCELLED` for user cancellation.
- Timeout closing for stale unpaid orders using Spring `@Scheduled` polling.
- Order status transition from `UNPAID` to `CLOSED` for timeout closing.
- Configuration for unpaid order timeout minutes, defaulting to `30`.
- Mapper, service, controller, migration, and scheduled-task tests.
- Reqable/API testing documentation updates.

This phase will not implement:

- Real WeChat, Alipay, card, or bank payment providers.
- External payment callback handling.
- Shipment status such as `SHIPPED`.
- Completion status such as `COMPLETED`.
- Refund, partial refund, return, or after-sales flows.
- MQ delay queues or distributed schedulers.
- Multi-warehouse inventory.
- Discounts, coupons, or split payments.

## Status Model

Order statuses used by this phase:

- `UNPAID`: order has been created and inventory is locked.
- `PAID`: mock payment succeeded and locked inventory has been confirmed as sold.
- `CANCELLED`: current user manually cancelled an unpaid order and locked inventory was released.
- `CLOSED`: system timeout closed an unpaid order and locked inventory was released.

Payment statuses used by this phase:

- `SUCCESS`: mock payment completed inside the backend transaction.

`PENDING` and `FAILED` are not needed in the first mock implementation because there is no asynchronous provider call. They can be introduced when real payment initiation and callbacks exist.

## Database Design

Create Flyway migration `src/main/resources/db/migration/V7__payment_schema.sql`.

Add closure fields to `sales_order`:

```sql
ALTER TABLE sales_order
    ADD COLUMN closed_at DATETIME(6) NULL AFTER paid_at,
    ADD COLUMN close_reason VARCHAR(255) NULL AFTER closed_at;
```

Create `payment` table:

```sql
CREATE TABLE payment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(64) NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(128) NOT NULL,
    paid_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_order_success (order_id, status),
    KEY idx_payment_order_no (order_no),
    KEY idx_payment_user_created (user_id, created_at),
    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES sales_order(id),
    CONSTRAINT fk_payment_user FOREIGN KEY (user_id) REFERENCES user_account(id)
);
```

Rationale:

- `order_id` is the internal relational boundary and should be used for joins and foreign keys.
- `order_no` is kept for user-facing lookup, logs, and easier API debugging.
- `uk_payment_order_success (order_id, status)` prevents duplicate successful payments for the same order.
- `payment_no` is the internal payment business number.
- `transaction_id` can be generated as a UUID for mock payment, preserving a future external-provider field.

## API Design

### Mock Payment

```http
POST /api/payments/mock-pay
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request:

```json
{
  "orderNo": "ORD20260602113000123456"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "paymentNo": "PAY20260602114500123456",
    "orderNo": "ORD20260602113000123456",
    "amount": 358.00,
    "channel": "MOCK",
    "status": "SUCCESS",
    "transactionId": "9b59f0f4-8a8a-46fa-a8e5-35a29f2d4d91",
    "paidAt": "2026-06-02T11:45:00.123"
  },
  "error": null
}
```

Rules:

- The order must belong to the authenticated user.
- The order row must be locked with `FOR UPDATE` before status checks and stock changes.
- If the order is `UNPAID`, payment succeeds in one transaction.
- If the order is already `PAID`, return the existing successful payment record without changing inventory again.
- If the order is `CANCELLED` or `CLOSED`, reject payment with a client error because locked stock has already been released.
- Unknown order numbers return `404 not_found`.
- Blank order numbers return `400 bad_request`.

### Manual Cancel

```http
POST /api/orders/{orderNo}/cancel
Authorization: Bearer <accessToken>
Content-Type: application/json
```

Request:

```json
{
  "reason": "用户不想买了"
}
```

Response:

```json
{
  "success": true,
  "data": {
    "orderNo": "ORD20260602113000123456",
    "status": "CANCELLED",
    "totalAmount": 358.00,
    "items": [],
    "createdAt": "2026-06-02T11:30:00.123",
    "paidAt": null,
    "closedAt": "2026-06-02T11:40:00.123",
    "closeReason": "用户不想买了"
  },
  "error": null
}
```

Rules:

- The order must belong to the authenticated user.
- The order row must be locked with `FOR UPDATE` before status checks and stock changes.
- If the order is `UNPAID`, cancellation succeeds in one transaction.
- If the order is already `CANCELLED` or `CLOSED`, return the current order state without releasing inventory again.
- If the order is `PAID`, reject cancellation in this MVP because refunds and after-sales are out of scope.
- Empty reason is allowed. The service stores a default reason of `USER_CANCELLED`.
- Reason text should be capped at 255 characters to match the database column.

## Timeout Closing

Use Spring `@Scheduled` polling for the MVP.

Configuration:

```properties
order.unpaid-timeout-minutes=30
order.timeout-close-batch-size=50
order.timeout-close-fixed-delay-ms=60000
```

Behavior:

- Every configured delay, query up to the configured batch size of orders where `status = 'UNPAID'` and `created_at < now - timeout`.
- For each candidate, call the same internal close method used by manual cancellation, but without an authenticated-user ownership check.
- The internal method must re-lock the order row with `FOR UPDATE` and re-check `UNPAID` before releasing stock.
- Timeout-closed orders use status `CLOSED`.
- Timeout close reason is `TIMEOUT_UNPAID_30_MINUTES` when the default timeout is used.

Rationale:

- A scheduled poller is enough for a single-node MVP and avoids MQ scope.
- Re-locking each order row prevents the scheduled task from racing with a user payment or manual cancellation.
- The batch size prevents one scheduled execution from monopolizing the database if many unpaid orders expire.

## Service Boundaries

### Payment Module

Create a `payment` module with `api`, `service`, `mapper`, `model`, and `dto` packages.

Responsibilities:

- Expose mock payment API.
- Validate user-owned order payment requests.
- Create successful mock payment records.
- Return existing successful payment records for already-paid orders.
- Coordinate with order and inventory persistence through a single transaction.

The payment module should not:

- Accept `userId`, amount, channel, or status from the request body.
- Talk to Python AI services.
- Implement real external payment calls.
- Own order creation or cart cleanup.

### Order Module Additions

Extend the order module for cancellation and timeout closing.

Responsibilities:

- Lock user-owned orders by `userId + orderNo`.
- Lock system-owned timeout candidates by `orderNo`.
- Update order status, `paid_at`, `closed_at`, and `close_reason`.
- Read order items for stock confirmation or release.
- Expose user cancellation API.

The order module should not:

- Create payment records.
- Generate external payment transaction IDs.
- Implement refunds or shipment.

### Inventory Mapper Additions

Add two atomic inventory transitions:

```sql
UPDATE inventory
SET locked_stock = locked_stock - #{quantity},
    sold_stock = sold_stock + #{quantity},
    updated_at = CURRENT_TIMESTAMP(6)
WHERE sku_id = #{skuId}
  AND locked_stock >= #{quantity}
```

```sql
UPDATE inventory
SET locked_stock = locked_stock - #{quantity},
    available_stock = available_stock + #{quantity},
    updated_at = CURRENT_TIMESTAMP(6)
WHERE sku_id = #{skuId}
  AND locked_stock >= #{quantity}
```

Each method returns the affected row count. A return value of `0` means inventory state is inconsistent with the order item snapshot and should fail the transaction.

## Transaction Flow

### Mock Payment Success

1. Validate current user ID.
2. Validate request and normalize `orderNo`.
3. Lock order by `userId + orderNo` using `FOR UPDATE`.
4. If order is `PAID`, return existing successful payment.
5. If order is `CANCELLED` or `CLOSED`, reject payment.
6. Validate order is `UNPAID`.
7. Read order items by `order_id`.
8. For each item, atomically move `locked_stock` to `sold_stock`.
9. Insert a `payment` row with channel `MOCK`, status `SUCCESS`, and UUID transaction ID.
10. Update order status to `PAID` and set `paid_at`.
11. Return the payment response.

All steps run in one `@Transactional` service method.

### Manual Cancellation

1. Validate current user ID.
2. Validate and normalize `orderNo`.
3. Lock order by `userId + orderNo` using `FOR UPDATE`.
4. If order is `CANCELLED` or `CLOSED`, return the current order response.
5. If order is `PAID`, reject cancellation.
6. Validate order is `UNPAID`.
7. Read order items by `order_id`.
8. For each item, atomically move `locked_stock` back to `available_stock`.
9. Update order status to `CANCELLED`, set `closed_at`, and store close reason.
10. Return the updated order response.

All steps run in one `@Transactional` service method.

### Timeout Close

1. Scheduled task finds expired `UNPAID` order numbers in batches.
2. For each order number, call an internal close method.
3. Lock the order by `orderNo` using `FOR UPDATE`.
4. If the order is no longer `UNPAID`, skip it.
5. Read order items by `order_id`.
6. Release locked inventory back to available inventory.
7. Update order status to `CLOSED`, set `closed_at`, and store timeout reason.

Each order should close in its own transaction so one inconsistent order does not block the whole batch.

## Concurrency and Idempotency

The design protects these races:

- Repeated mock payment clicks: first request pays the order; later requests return the existing success record.
- Payment racing with manual cancellation: row lock ensures only one state transition wins.
- Payment racing with timeout close: row lock ensures only one state transition wins.
- Manual cancellation repeated: later requests return the already closed order state.
- Timeout task seeing stale candidates: row lock and status re-check prevents duplicate stock release.

The service should treat inventory transition affected rows of `0` as a serious consistency failure and roll back the transaction.

## Error Handling

Expected errors:

- `400 bad_request`: blank order number, invalid close reason length, payment for closed order, cancellation of paid order.
- `404 not_found`: current user does not own the order or the order does not exist.
- Existing global exception handling should wrap these errors in the current `ApiResponse` error format.

The MVP does not need a new exception type unless the existing `BadRequestException` and `ResourceNotFoundException` are insufficient in implementation.

## Testing Strategy

Migration tests:

- `payment` table exists.
- `payment` has unique `payment_no`.
- `payment` has foreign keys to `sales_order` and `user_account`.
- `sales_order` has `closed_at` and `close_reason`.

Mapper tests:

- Insert and query successful payment by order ID.
- Query existing successful payment for an already-paid order.
- Lock order by `userId + orderNo`.
- Lock order by `orderNo` for timeout close.
- Update order to `PAID` with `paid_at`.
- Update order to `CANCELLED` or `CLOSED` with `closed_at` and `close_reason`.
- Find expired unpaid orders in batch order.
- Confirm locked inventory to sold inventory.
- Release locked inventory to available inventory.

Service tests:

- Mock payment succeeds for current user's `UNPAID` order.
- Mock payment moves `locked_stock` to `sold_stock`.
- Mock payment creates one successful payment record.
- Repeated mock payment returns existing payment and does not move inventory again.
- Mock payment rejects another user's order.
- Mock payment rejects `CANCELLED` and `CLOSED` orders.
- Manual cancellation succeeds for current user's `UNPAID` order.
- Manual cancellation releases locked inventory.
- Repeated manual cancellation does not release inventory twice.
- Manual cancellation rejects `PAID` orders.
- Timeout close closes expired unpaid orders and releases inventory.
- Timeout close skips orders that became `PAID`, `CANCELLED`, or `CLOSED`.

Controller tests:

- `POST /api/payments/mock-pay` requires authentication.
- `POST /api/payments/mock-pay` returns payment response on success.
- `POST /api/payments/mock-pay` handles repeat requests idempotently.
- `POST /api/orders/{orderNo}/cancel` requires authentication.
- `POST /api/orders/{orderNo}/cancel` returns updated order response.
- User isolation is enforced for payment and cancellation.

Full verification:

```powershell
.\mvnw.cmd verify
```

## Documentation Updates

After implementation, update:

- `docs/backend-feature-mapping.md` with payment and cancellation status.
- `docs/api-testing-with-reqable.md` with mock payment, manual cancellation, and timeout-close verification notes.
- `src/docs/asciidoc/api.adoc` if the project expects new REST Docs snippets for these endpoints.

## Approval Gate

Development must not start until this design is reviewed and approved by the user. After approval, the next step is to write a separate implementation plan under `docs/superpowers/plans/`, then implement task by task with tests.
