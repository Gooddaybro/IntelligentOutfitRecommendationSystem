# Mock Payment and Order Cancellation MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the mock payment and unpaid-order cancellation loop so paid orders confirm locked inventory as sold, cancelled or timed-out orders release locked inventory, and every successful mock payment has a persisted payment record.

**Architecture:** Continue the Spring Boot modular monolith. The new `payment` module owns the mock payment public API and payment records, while the existing `order` module owns order cancellation and timeout closing. `InventoryMapper` remains the single SQL boundary for atomic stock transitions.

**Tech Stack:** Java 17, Spring Boot, Spring MVC, Spring Security, Spring Scheduling, MyBatis XML, Flyway, H2/MySQL-compatible migrations, JUnit 5, Mockito, MockMvc, AssertJ, Maven Checkstyle.

---

## Comment and Contract Rules

- Read `docs/commenting-guidelines.md` before Java, Mapper XML, or API contract changes.
- Every new top-level Java type in `src/main/java` needs class-level Javadoc explaining responsibility and business boundary.
- Mapper XML comments must explain transaction and ownership boundaries for complex SQL.
- Service comments should explain why row locks, idempotency, and inventory transitions happen at that layer.
- Run `.\mvnw.cmd verify` before claiming the backend work is complete.

## File Structure

- Create `src/main/resources/db/migration/V7__payment_schema.sql`: add `closed_at` and `close_reason` to `sales_order`; create `payment`.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/mapper/InventoryMapper.java`: add `confirmSoldStock` and `releaseLockedStock`.
- Modify `src/main/resources/mapper/inventory/InventoryMapper.xml`: add atomic stock transition SQL.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/SalesOrder.java`: add closure fields.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/OrderResponse.java`: expose closure fields.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/CancelOrderRequest.java`: manual cancellation request contract.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/mapper/OrderMapper.java`: add row-locking and status update methods.
- Modify `src/main/resources/mapper/order/OrderMapper.xml`: add `FOR UPDATE`, status updates, and expired-order query.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/api/OrderController.java`: add cancellation endpoint.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java`: add cancellation and timeout close behavior.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/Payment.java`: payment persistence model.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/MockPaymentRequest.java`: mock pay request.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentResponse.java`: payment response contract.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/mapper/PaymentMapper.java`: payment data access boundary.
- Create `src/main/resources/mapper/payment/PaymentMapper.xml`: payment insert/query SQL.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`: mock payment transaction boundary.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/api/PaymentController.java`: public mock payment endpoint.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderTimeoutProperties.java`: timeout configuration.
- Create `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderTimeoutScheduler.java`: scheduled timeout closer.
- Modify `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplication.java`: enable configuration properties and scheduling.
- Modify `src/main/resources/application.properties`: add default timeout settings.
- Modify `src/test/resources/application-test.properties`: add test timeout settings.
- Modify `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`: assert new schema.
- Modify `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryMapperTests.java`: cover stock confirmation and release.
- Modify `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderMapperTests.java`: cover row locking, status updates, and expired order query.
- Modify `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java`: cover manual cancellation and timeout closing.
- Create `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentMapperTests.java`: cover payment persistence.
- Create `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`: cover mock payment service behavior and idempotency.
- Create `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentControllerTests.java`: cover authenticated mock payment API.
- Modify `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderControllerTests.java`: cover manual cancellation API.
- Modify `docs/backend-feature-mapping.md`: update feature status.
- Modify `docs/api-testing-with-reqable.md`: add manual API testing steps.

### Task 1: Schema, Order Model, and Response Contract

**Files:**
- Create: `src/main/resources/db/migration/V7__payment_schema.sql`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/SalesOrder.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/OrderResponse.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java`
- Modify: `src/main/resources/mapper/order/OrderMapper.xml`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderControllerTests.java`

- [ ] **Step 1: Write failing migration and response tests**

Add assertions to `MySqlFlywayMigrationTests`:

```java
Integer paymentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payment", Integer.class);
Integer closedAtColumnCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'sales_order'
          AND COLUMN_NAME = 'closed_at'
        """, Integer.class);
Integer closeReasonColumnCount = jdbcTemplate.queryForObject("""
        SELECT COUNT(*)
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_NAME = 'sales_order'
          AND COLUMN_NAME = 'close_reason'
        """, Integer.class);

assertThat(paymentCount).isZero();
assertThat(closedAtColumnCount).isOne();
assertThat(closeReasonColumnCount).isOne();
```

Add response assertions to `OrderControllerTests.createsOrderFromCurrentUsersCartThenListsAndReadsDetail`:

```java
.andExpect(jsonPath("$.data.paidAt").doesNotExist())
.andExpect(jsonPath("$.data.closedAt").doesNotExist())
.andExpect(jsonPath("$.data.closeReason").doesNotExist())
```

- [ ] **Step 2: Run red tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderControllerTests,MySqlFlywayMigrationTests test
```

Expected: `MySqlFlywayMigrationTests` fails when `RUN_MYSQL_TESTS=true` because `payment`, `closed_at`, and `close_reason` do not exist; `OrderControllerTests` fails because response fields are missing once the assertions are active.

- [ ] **Step 3: Add schema and contract implementation**

Create `V7__payment_schema.sql`:

```sql
ALTER TABLE sales_order
    ADD COLUMN closed_at DATETIME(6) NULL AFTER paid_at,
    ADD COLUMN close_reason VARCHAR(255) NULL AFTER closed_at;

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

Add `closedAt` and `closeReason` to `SalesOrder`, `OrderResponse`, order mapper selects, and `OrderService#toResponse`.

- [ ] **Step 4: Run green tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderControllerTests test
```

Expected: PASS for local H2-backed controller tests.

### Task 2: Inventory Confirmation and Release

**Files:**
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/mapper/InventoryMapper.java`
- Modify: `src/main/resources/mapper/inventory/InventoryMapper.xml`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryMapperTests.java`

- [ ] **Step 1: Write failing inventory mapper tests**

Add:

```java
@Test
void confirmSoldStockMovesLockedStockToSoldStock() {
    assertThat(mapper.lockStock(2103L, 2)).isEqualTo(1);

    assertThat(mapper.confirmSoldStock(2103L, 2)).isEqualTo(1);

    var inventory = mapper.findBySkuId(2103L);
    assertThat(inventory.getLockedStock()).isZero();
    assertThat(inventory.getSoldStock()).isEqualTo(2);
}

@Test
void releaseLockedStockMovesLockedStockBackToAvailableStock() {
    assertThat(mapper.lockStock(2203L, 2)).isEqualTo(1);

    assertThat(mapper.releaseLockedStock(2203L, 2)).isEqualTo(1);

    var inventory = mapper.findBySkuId(2203L);
    assertThat(inventory.getAvailableStock()).isEqualTo(7);
    assertThat(inventory.getLockedStock()).isZero();
}
```

- [ ] **Step 2: Run red test**

Run:

```powershell
.\mvnw.cmd -Dtest=InventoryMapperTests test
```

Expected: compilation fails because `confirmSoldStock` and `releaseLockedStock` do not exist.

- [ ] **Step 3: Add mapper methods and SQL**

Add methods:

```java
int confirmSoldStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);

int releaseLockedStock(@Param("skuId") Long skuId, @Param("quantity") Integer quantity);
```

Add XML updates with comments explaining payment confirmation and cancellation release boundaries.

- [ ] **Step 4: Run green test**

Run:

```powershell
.\mvnw.cmd -Dtest=InventoryMapperTests test
```

Expected: PASS.

### Task 3: Order Cancellation and Timeout Close

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/CancelOrderRequest.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/mapper/OrderMapper.java`
- Modify: `src/main/resources/mapper/order/OrderMapper.xml`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/api/OrderController.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderMapperTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderControllerTests.java`

- [ ] **Step 1: Write failing order mapper and service tests**

Add mapper tests for `updateOrderClosed`, `findExpiredUnpaidOrderNos`, and user-scoped reads after closure.

Add service tests:

```java
@Test
void cancelUnpaidOrderReleasesLockedStockAndClosesOrder() {
    SalesOrder order = unpaidOrder(88L, 10L, "ORDCANCEL1");
    when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDCANCEL1")).thenReturn(order);
    when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
    when(inventoryMapper.releaseLockedStock(2102L, 2)).thenReturn(1);

    var response = service.cancelOrder(10L, "ORDCANCEL1", new CancelOrderRequest("用户不想买了"));

    verify(orderMapper).updateOrderClosed(88L, "CANCELLED", "用户不想买了");
    assertThat(response.status()).isEqualTo("CANCELLED");
}

@Test
void closeExpiredOrderSkipsPaidOrderWithoutReleasingStock() {
    SalesOrder order = paidOrder(88L, 10L, "ORDPAID1");
    when(orderMapper.findOrderByOrderNoForUpdate("ORDPAID1")).thenReturn(order);

    service.closeExpiredOrder("ORDPAID1", "TIMEOUT_UNPAID_30_MINUTES");

    verify(inventoryMapper, never()).releaseLockedStock(any(), any());
    verify(orderMapper, never()).updateOrderClosed(any(), any(), any());
}
```

Add controller test:

```java
mockMvc.perform(post("/api/orders/{orderNo}/cancel", orderNo)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"用户不想买了\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("CANCELLED"))
        .andExpect(jsonPath("$.data.closedAt").isNotEmpty())
        .andExpect(jsonPath("$.data.closeReason").value("用户不想买了"));
```

- [ ] **Step 2: Run red tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderMapperTests,OrderServiceTests,OrderControllerTests test
```

Expected: compilation fails because cancellation request, mapper methods, service methods, and controller endpoint do not exist.

- [ ] **Step 3: Implement cancellation and timeout close**

Implement:

- `CancelOrderRequest(String reason)` with Javadoc.
- `OrderMapper#findOrderByUserIdAndOrderNoForUpdate`.
- `OrderMapper#findOrderByOrderNoForUpdate`.
- `OrderMapper#updateOrderClosed`.
- `OrderMapper#findExpiredUnpaidOrderNos`.
- `OrderService#cancelOrder`.
- `OrderService#closeExpiredOrder`.
- `OrderController#cancelOrder`.

Cancellation must release inventory only for `UNPAID` orders and must treat `CANCELLED`/`CLOSED` as idempotent.

- [ ] **Step 4: Run green tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderMapperTests,OrderServiceTests,OrderControllerTests test
```

Expected: PASS.

### Task 4: Payment Module and Mock Pay API

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/Payment.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/MockPaymentRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/mapper/PaymentMapper.java`
- Create: `src/main/resources/mapper/payment/PaymentMapper.xml`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/api/PaymentController.java`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentMapperTests.java`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentControllerTests.java`

- [ ] **Step 1: Write failing payment mapper, service, and controller tests**

Payment service tests must cover:

```java
@Test
void mockPayUnpaidOrderConfirmsStockCreatesPaymentAndMarksOrderPaid() {
    SalesOrder order = unpaidOrder(88L, 10L, "ORDPAY1");
    when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
    when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
    when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);

    var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

    verify(paymentMapper).insertPayment(any(Payment.class));
    verify(orderMapper).updateOrderPaid(eq(88L), any());
    assertThat(response.status()).isEqualTo("SUCCESS");
}

@Test
void mockPayPaidOrderReturnsExistingPaymentWithoutMovingInventoryAgain() {
    SalesOrder order = paidOrder(88L, 10L, "ORDPAY1");
    when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
    when(paymentMapper.findSuccessByOrderId(88L)).thenReturn(successPayment(88L, "ORDPAY1"));

    var response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

    verify(inventoryMapper, never()).confirmSoldStock(any(), any());
    verify(paymentMapper, never()).insertPayment(any(Payment.class));
    assertThat(response.status()).isEqualTo("SUCCESS");
}
```

Payment controller tests must cover authentication, success response, and idempotent second call.

- [ ] **Step 2: Run red tests**

Run:

```powershell
.\mvnw.cmd -Dtest=PaymentMapperTests,PaymentServiceTests,PaymentControllerTests test
```

Expected: compilation fails because the payment package does not exist.

- [ ] **Step 3: Implement payment module**

Implement:

- `Payment` model with all `payment` table columns.
- `MockPaymentRequest` record with `@NotBlank`.
- `PaymentResponse` record exposing `paymentNo`, `orderNo`, `amount`, `channel`, `status`, `transactionId`, `paidAt`.
- `PaymentMapper#insertPayment`.
- `PaymentMapper#findSuccessByOrderId`.
- `PaymentService#mockPay`.
- `PaymentController#mockPay`.

Mock payment must generate `PAY` business number and UUID transaction ID inside the backend, never from request body.

- [ ] **Step 4: Run green tests**

Run:

```powershell
.\mvnw.cmd -Dtest=PaymentMapperTests,PaymentServiceTests,PaymentControllerTests test
```

Expected: PASS.

### Task 5: Timeout Scheduler and Configuration

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderTimeoutProperties.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderTimeoutScheduler.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplication.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java`

- [ ] **Step 1: Write failing scheduler/service tests**

Add a test that `closeExpiredOrders` reads expired order numbers and delegates each order to `closeExpiredOrder`.

Add configuration expectations:

```properties
order.unpaid-timeout-minutes=30
order.timeout-close-batch-size=50
order.timeout-close-fixed-delay-ms=60000
```

- [ ] **Step 2: Run red tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderServiceTests test
```

Expected: compilation fails because properties and scheduler types do not exist.

- [ ] **Step 3: Implement scheduler**

Implement:

- `OrderTimeoutProperties` as a configuration properties type.
- `OrderTimeoutScheduler` with `@Scheduled(fixedDelayString = "${order.timeout-close-fixed-delay-ms:60000}")`.
- `OrderService#closeExpiredOrders` to find candidates using configured cutoff and batch size.

- [ ] **Step 4: Run green tests**

Run:

```powershell
.\mvnw.cmd -Dtest=OrderServiceTests test
```

Expected: PASS.

### Task 6: Documentation, Full Verification, and Commit

**Files:**
- Modify: `docs/backend-feature-mapping.md`
- Modify: `docs/api-testing-with-reqable.md`
- Modify: `src/docs/asciidoc/api.adoc` only if REST Docs snippets require endpoint references.

- [ ] **Step 1: Update docs**

Add API examples:

```http
POST {{base_url}}/api/payments/mock-pay
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "orderNo": "{{order_no}}"
}
```

Add cancellation example:

```http
POST {{base_url}}/api/orders/{{order_no}}/cancel
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "reason": "用户不想买了"
}
```

- [ ] **Step 2: Run targeted tests**

Run:

```powershell
.\mvnw.cmd -Dtest=InventoryMapperTests,OrderMapperTests,OrderServiceTests,OrderControllerTests,PaymentMapperTests,PaymentServiceTests,PaymentControllerTests test
```

Expected: PASS.

- [ ] **Step 3: Run full verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: Maven exits with code `0`; tests pass and Checkstyle reports no violations.

- [ ] **Step 4: Review diff and commit**

Run:

```powershell
git status --short
git diff --check
git diff --stat
```

Expected: only payment, order, inventory, config, tests, and documentation files changed; no whitespace errors.

Commit:

```powershell
git add docs src
git commit -m "feat: add mock payment and order cancellation"
```

## Self-Review Checklist

- Spec coverage: payment table, mock payment, paid status, stock confirmation, manual cancellation, timeout closing, stock release, idempotency, API docs, and Maven verification are covered.
- Placeholder scan: task bodies are complete and do not rely on unresolved markers or incomplete file names.
- Type consistency: plan uses `Payment`, `MockPaymentRequest`, `PaymentResponse`, `CancelOrderRequest`, `OrderTimeoutProperties`, and `OrderTimeoutScheduler` consistently.
