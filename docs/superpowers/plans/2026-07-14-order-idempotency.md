# Order Creation Idempotency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Implementation status (updated 2026-07-15):** Code and automated H2 verification complete. Learning demos have been moved out of production sources, so full Maven verify and Checkstyle now pass. Real MySQL Testcontainers execution remains blocked because this machine has no Docker daemon.

**Goal:** Make cart checkout and buy-now idempotent for 24 hours with a client UUID, a MySQL unique constraint, atomic order creation, safe replay, conflict detection, and bounded cleanup.

**Architecture:** `OrderService` normalizes business input and delegates execution to an `OrderIdempotencyCoordinator`. The coordinator uses `TransactionTemplate` so the idempotency claim, inventory update, order rows, cart cleanup, behavior event, and `order_id` link commit or roll back together; duplicate-claim resolution happens outside the failed transaction. Redis remains outside the correctness path.

**Tech Stack:** Java 21, Spring Boot 4, Spring TransactionTemplate, MyBatis, Flyway, MySQL 8, H2 MySQL mode, JUnit 5, Mockito, MockMvc, Testcontainers.

---

## File map

**Create:**

- `backend/src/main/resources/db/migration/V16__order_idempotency_schema.sql` — table, unique key, foreign keys, expiry index.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/OrderIdempotencyRecord.java` — persistence model.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/OrderOperation.java` — stable operation scope.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/mapper/OrderIdempotencyMapper.java` — claim, resolve, link, and cleanup persistence port.
- `backend/src/main/resources/mapper/order/OrderIdempotencyMapper.xml` — MyBatis SQL.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderRequestFingerprint.java` — canonical request hashing.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderCreationResult.java` — internal order ID plus public response.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/IdempotentOrderResult.java` — response plus replay marker.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderIdempotencyCoordinator.java` — transaction and duplicate-claim state machine.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderIdempotencyProperties.java` — retention and cleanup settings.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderIdempotencyCleanupScheduler.java` — bounded periodic cleanup.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/IdempotencyKeyConflictException.java` — explicit HTTP 409 business failure.
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderRequestFingerprintTests.java`.
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMapperTests.java`.
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyCoordinatorTests.java`.
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyCleanupSchedulerTests.java`.
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMySqlConcurrencyTests.java`.

**Modify:**

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplication.java` — enable new configuration properties.
- `backend/src/main/resources/application.properties` and `backend/src/test/resources/application-test.properties` — defaults.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/mapper/OrderMapper.java` and `backend/src/main/resources/mapper/order/OrderMapper.xml` — replay lookup by `order_id + user_id`.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java` — route both create commands through the coordinator.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/api/OrderController.java` — header contract and replay response header.
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/GlobalExceptionHandler.java` — map conflict to HTTP 409.
- Existing order tests and MySQL migration test — adapt signatures and verify the new schema.
- `docs/superpowers/specs/2026-07-14-order-idempotency-design.md` and the Java architecture document — record implemented status and evidence after verification.

## Task 1: Add the MySQL idempotency persistence model

**Files:** migration, model, enum, mapper, mapper XML, `OrderIdempotencyMapperTests`, `MySqlFlywayMigrationTests`.

- [x] **Step 1: Write a failing Mapper integration test**

Add tests that insert a claim, read it by `(userId, operation, key)`, link it to an existing seeded order, and assert a second identical claim violates the unique key:

```java
@Test
void storesLinksAndFindsOrderIdempotencyClaim() {
    OrderIdempotencyRecord record = record("CART_CHECKOUT", UUID.randomUUID().toString());
    mapper.insert(record);
    assertThat(record.getId()).isPositive();

    assertThat(mapper.linkOrder(record.getId(), 9201L)).isOne();
    OrderIdempotencyRecord stored = mapper.findByKey(
            9001L, "CART_CHECKOUT", record.getIdempotencyKey()
    );
    assertThat(stored.getOrderId()).isEqualTo(9201L);
}

@Test
void rejectsDuplicateUserOperationAndKey() {
    String key = UUID.randomUUID().toString();
    mapper.insert(record("BUY_NOW", key));
    assertThatThrownBy(() -> mapper.insert(record("BUY_NOW", key)))
            .isInstanceOf(DuplicateKeyException.class);
}
```

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyMapperTests" test
```

Expected: test compilation fails because the model and mapper do not exist.

- [x] **Step 3: Add `V16__order_idempotency_schema.sql`**

Use the exact schema approved in the design: `CHAR(36)` Key, `CHAR(64)` fingerprint, nullable `order_id`, unique `(user_id, operation, idempotency_key)`, expiry index, and user/order foreign keys.

- [x] **Step 4: Add the model and stable operation enum**

```java
public enum OrderOperation {
    CART_CHECKOUT,
    BUY_NOW
}
```

```java
@Data
public class OrderIdempotencyRecord {
    private Long id;
    private Long userId;
    private String operation;
    private String idempotencyKey;
    private String requestFingerprint;
    private Long orderId;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

Both top-level types require boundary-focused Javadoc.

- [x] **Step 5: Add mapper methods and SQL**

```java
void insert(OrderIdempotencyRecord record);

int deleteExpiredKey(Long userId, String operation, String idempotencyKey, LocalDateTime now);

OrderIdempotencyRecord findByKey(Long userId, String operation, String idempotencyKey);

int linkOrder(Long id, Long orderId);

List<Long> findExpiredIds(LocalDateTime now, int batchSize);

int deleteByIds(List<Long> ids);
```

`insert` must use generated keys. `linkOrder` must update only rows whose `order_id IS NULL`. Cleanup must first select ordered IDs with `LIMIT`, then delete those exact IDs.

- [x] **Step 6: Extend the real-MySQL migration assertion**

Add `SELECT COUNT(*) FROM order_idempotency` and an `INFORMATION_SCHEMA.STATISTICS` assertion for `uk_order_idempotency` to `MySqlFlywayMigrationTests`.

- [x] **Step 7: Run GREEN and Checkstyle**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyMapperTests" test
.\mvnw.cmd checkstyle:check "-Dcheckstyle.includes=**/order/**/*.java"
```

Expected: mapper tests pass and order production code has zero violations.

- [x] **Step 8: Commit**

```powershell
git add backend/src/main backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMapperTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java
git commit -m "feat: add order idempotency persistence"
```

## Task 2: Add canonical request fingerprints

**Files:** `OrderRequestFingerprint.java`, `OrderRequestFingerprintTests.java`.

- [x] **Step 1: Write failing fingerprint tests**

```java
@Test
void cartFingerprintIgnoresSkuOrderAndDuplicates() {
    String first = fingerprint.cart(List.of(2102L, 2202L, 2102L));
    String second = fingerprint.cart(List.of(2202L, 2102L));
    assertThat(first).isEqualTo(second).hasSize(64);
}

@Test
void buyNowFingerprintChangesWithQuantity() {
    assertThat(fingerprint.buyNow(2102L, 1))
            .isNotEqualTo(fingerprint.buyNow(2102L, 2));
}

@Test
void operationIsPartOfCanonicalInput() {
    assertThat(fingerprint.hash("CART_CHECKOUT|skuId=2102|quantity=1"))
            .isNotEqualTo(fingerprint.buyNow(2102L, 1));
}
```

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderRequestFingerprintTests" test
```

Expected: compilation fails because `OrderRequestFingerprint` is missing.

- [x] **Step 3: Implement deterministic UTF-8 SHA-256 hashing**

Expose only the business APIs:

```java
public String cart(List<Long> skuIds)
public String buyNow(Long skuId, Integer quantity)
```

The cart method must `distinct().sorted()` before joining. Canonical inputs must begin with `CART_CHECKOUT|source=CART|` or `BUY_NOW|` respectively. Keep the raw `hash` helper package-private for focused testing, not public API.

- [x] **Step 4: Run GREEN and commit**

```powershell
.\mvnw.cmd "-Dtest=OrderRequestFingerprintTests" test
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderRequestFingerprint.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderRequestFingerprintTests.java
git commit -m "feat: fingerprint order creation requests"
```

## Task 3: Implement the transaction coordinator

**Files:** coordinator, properties, result records, conflict exception, coordinator tests.

- [x] **Step 1: Write failing tests for Key validation and first execution**

Use a mocked mapper and a real `TransactionTemplate` backed by a mocked `PlatformTransactionManager`: return a mocked `TransactionStatus` from `getTransaction`, and leave `commit`/`rollback` as Mockito no-ops. Assert invalid UUIDs never call the mapper and a first claim calls the supplied action exactly once.

```java
assertThatThrownBy(() -> execute("not-a-uuid", "abc", action))
        .isInstanceOf(BadRequestException.class)
        .hasMessage("Idempotency-Key must be a valid UUID");
verifyNoInteractions(mapper);

IdempotentOrderResult result = execute(UUID.randomUUID().toString(), "abc", action);
assertThat(result.replayed()).isFalse();
assertThat(result.order().orderNo()).isEqualTo("ORD-1");
verify(mapper).linkOrder(41L, 91L);
```

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyCoordinatorTests" test
```

Expected: compilation fails for the missing coordinator/result types.

- [x] **Step 3: Add configuration and result records**

```java
@ConfigurationProperties(prefix = "app.order.idempotency")
public class OrderIdempotencyProperties {
    private Duration retention = Duration.ofHours(24);
    private Duration cleanupDelay = Duration.ofHours(1);
    private int cleanupBatchSize = 500;
    // conventional getters and setters
}
```

```java
public record OrderCreationResult(Long orderId, OrderResponse order) {
}

public record IdempotentOrderResult(OrderResponse order, boolean replayed) {
}
```

Register the properties in `IntelligentOutfitRecommendationSystemApplication` and add production/test defaults.

- [x] **Step 4: Add conflict exception and HTTP mapping**

`IdempotencyKeyConflictException` carries the approved message. `GlobalExceptionHandler` maps it to HTTP 409 and error code `idempotency_key_reused`.

- [x] **Step 5: Implement first execution in `TransactionTemplate`**

The public coordinator API is:

```java
public IdempotentOrderResult execute(
        Long userId,
        OrderOperation operation,
        String rawKey,
        String fingerprint,
        Supplier<OrderCreationResult> createAction,
        Function<Long, OrderResponse> replayLoader
)
```

Inside the transaction: delete an expired matching Key, insert the claim, execute `createAction`, require non-null `orderId`, link the row, and return `replayed=false`.

- [x] **Step 6: Write RED tests for duplicate replay, conflict, and action failure**

Simulate only the claim insert throwing `DuplicateKeyException`. Assert same fingerprint loads the existing `orderId`; a different fingerprint throws `IdempotencyKeyConflictException`; an exception from the action escapes and does not enter replay logic.

- [x] **Step 7: Implement duplicate resolution outside the failed transaction**

Wrap `DuplicateKeyException` thrown specifically by `mapper.insert` in a private `IdempotencyClaimConflictException`. Catch only that marker outside `TransactionTemplate`; do not reinterpret duplicate keys thrown by order insertion or event persistence.

If an existing row has expired at the resolution boundary, allow exactly one new claim attempt. Missing, unlinked, or repeatedly expired records must fail closed with `IllegalStateException` rather than loop.

- [x] **Step 8: Run GREEN and commit**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyCoordinatorTests" test
.\mvnw.cmd checkstyle:check "-Dcheckstyle.includes=**/order/**/*.java,**/common/error/**/*.java"
git add backend/src/main backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyCoordinatorTests.java
git commit -m "feat: coordinate idempotent order transactions"
```

## Task 4: Route order creation through the coordinator

**Files:** `OrderService`, `OrderMapper`, `OrderMapper.xml`, `OrderServiceTests`.

- [x] **Step 1: Adapt service tests first**

Change calls to include a UUID and assert `response.order()` fields. Stub the coordinator with `thenAnswer` so business tests execute the supplied `createAction`; add explicit tests that cart and buy-now pass different operations and stable fingerprints.

```java
when(coordinator.execute(eq(10L), eq(OrderOperation.CART_CHECKOUT), eq(KEY), anyString(), any(), any()))
        .thenAnswer(invocation -> {
            Supplier<OrderCreationResult> action = invocation.getArgument(4);
            return new IdempotentOrderResult(action.get().order(), false);
        });
```

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTests" test
```

Expected: compilation/signature failures because service methods do not accept a Key or return the new result.

- [x] **Step 3: Change the public creation signatures**

```java
public IdempotentOrderResult createOrder(Long userId, String idempotencyKey, CreateOrderRequest request)
public IdempotentOrderResult buyNow(Long userId, String idempotencyKey, BuyNowRequest request)
```

Remove `@Transactional` from these two public methods only. Their private creation action now returns `OrderCreationResult(order.getId(), response)`, and it runs inside the coordinator transaction. Cancellation and timeout-closing transactions remain unchanged.

- [x] **Step 4: Add owner-scoped replay lookup**

```java
SalesOrder findOrderByUserIdAndId(Long userId, Long orderId);
```

The SQL must require both columns. `OrderService` loads its items and maps the current order status for replay. Missing replay resources fail closed instead of creating another order.

- [x] **Step 5: Run GREEN and related regression tests**

```powershell
.\mvnw.cmd "-Dtest=OrderServiceTests,OrderMapperTests" test
```

- [x] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order backend/src/main/resources/mapper/order backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderMapperTests.java
git commit -m "feat: make order creation idempotent"
```

## Task 5: Enforce the HTTP contract

**Files:** `OrderController`, `OrderControllerTests`, `GlobalExceptionHandler` tests if present.

- [x] **Step 1: Write failing MockMvc tests**

For both endpoints add cases for missing Key, invalid UUID, first response header, same-Key replay, and different-payload conflict. Capture the first `orderNo` and assert the replay matches it:

```java
mockMvc.perform(post("/api/orders/buy-now")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(header().string("Idempotency-Replayed", "false"));
```

Repeat with the same Key and body expecting `true`; repeat with changed quantity expecting 409 and `idempotency_key_reused`.

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTests" test
```

Expected: missing Key is currently accepted and replay header is absent.

- [x] **Step 3: Modify controller signatures and response**

Read `@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey`. Return `ResponseEntity<ApiResponse<OrderResponse>>` and set `Idempotency-Replayed` from `IdempotentOrderResult`.

- [x] **Step 4: Add unique UUID headers to every existing create/buy-now test**

Do not add the header to list, detail, cancel, payment, or timeout endpoints. This preserves the agreed command boundary.

- [x] **Step 5: Run GREEN and commit**

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTests,OrderServiceTests" test
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/api/OrderController.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderControllerTests.java
git commit -m "feat: require order idempotency keys"
```

## Task 6: Add bounded expiry cleanup

**Files:** properties, cleanup scheduler, mapper tests, scheduler tests, property files.

- [x] **Step 1: Write failing cleanup tests**

Mapper test: insert two expired and one active record, select at most one expired ID, delete it, and assert the active record remains. Scheduler unit test: verify one scheduled invocation delegates once with the configured batch size.

- [x] **Step 2: Run RED**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyMapperTests,OrderIdempotencyCleanupSchedulerTests" test
```

- [x] **Step 3: Implement bounded cleanup**

`OrderIdempotencyCoordinator.cleanupExpired()` reads at most `cleanupBatchSize` ordered IDs and deletes only those IDs. `OrderIdempotencyCleanupScheduler` uses:

```java
@Scheduled(fixedDelayString = "${app.order.idempotency.cleanup-delay:PT1H}")
public void cleanupExpiredRecords() {
    coordinator.cleanupExpired();
}
```

Properties:

```properties
app.order.idempotency.retention=PT24H
app.order.idempotency.cleanup-delay=PT1H
app.order.idempotency.cleanup-batch-size=500
```

- [x] **Step 4: Run GREEN and commit**

```powershell
.\mvnw.cmd "-Dtest=OrderIdempotencyMapperTests,OrderIdempotencyCleanupSchedulerTests" test
git add backend/src/main backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMapperTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyCleanupSchedulerTests.java
git commit -m "feat: clean expired order idempotency records"
```

## Task 7: Prove rollback and sequential idempotency end to end

**Files:** `OrderControllerTests`, possibly a focused Spring integration test.

- [x] **Step 1: Add database-level acceptance assertions**

Autowire `JdbcTemplate` in the test. Around a unique user and SKU, assert first and repeated requests produce:

```text
sales_order delta = 1
order_idempotency delta = 1
inventory.locked_stock delta = requested quantity once
behavior_event ORDER_CREATED delta = 1
```

For a forced insufficient-stock request, assert no idempotency row and no order remain, then replenish/select a valid quantity and retry the same Key successfully.

- [x] **Step 2: Run RED/GREEN as needed without changing the contract**

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTests" test
```

Fix only transaction-boundary defects exposed by the test. Do not add Redis locks or persist failed outcomes.

- [x] **Step 3: Run all order tests and commit**

```powershell
.\mvnw.cmd "-Dtest=OrderControllerTests,OrderServiceTests,OrderMapperTests,OrderIdempotencyMapperTests,OrderIdempotencyCoordinatorTests,OrderIdempotencyCleanupSchedulerTests,OrderTimeoutSchedulerTests" test
git add backend/src/main backend/src/test
git commit -m "test: prove order idempotency transaction semantics"
```

## Task 8: Add a real MySQL concurrency proof

**Files:** `OrderIdempotencyMySqlConcurrencyTests`, `MySqlFlywayMigrationTests`.

- [x] **Step 1: Write the Testcontainers concurrency test**

Extend `BaseMySqlContainerTest`, gate it with the existing `RUN_MYSQL_TESTS=true`, and submit two `buyNow` calls with the same user, Key, SKU, and quantity through a two-thread executor and a start latch.

Assert both results have the same `orderNo`, exactly one result is replayed, the order count increases by one, the lock-stock delta equals one purchase, and the unique logical Key has one row.

- [ ] **Step 2: Run the test when Docker is available** — attempted; blocked by `Could not find a valid Docker environment`.

```powershell
$env:RUN_MYSQL_TESTS='true'
.\mvnw.cmd "-Dtest=OrderIdempotencyMySqlConcurrencyTests,MySqlFlywayMigrationTests" test
```

Expected with Docker: both tests pass. Without Docker: record the environment blocker explicitly; do not claim real MySQL concurrency was executed.

- [x] **Step 3: Commit**

```powershell
git add backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMySqlConcurrencyTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java
git commit -m "test: cover concurrent order idempotency"
```

## Task 9: Verification and documentation

**Files:** the two design documents and implementation plan checkboxes/status.

- [x] **Step 1: Run focused quality gates**

```powershell
.\mvnw.cmd "-Dtest=Order*Tests" test
.\mvnw.cmd checkstyle:check "-Dcheckstyle.includes=**/order/**/*.java,**/common/error/**/*.java"
```

Expected: all focused tests pass and modified production packages have zero Checkstyle violations.

- [x] **Step 2: Run full verification**

```powershell
.\mvnw.cmd verify
```

Record the exact test count and any existing learning-demo Checkstyle blockers. Do not silently modify user learning files as part of this feature.

- [x] **Step 3: Update documentation evidence**

Mark the design implemented only for behaviors actually verified. Add the final architecture, transaction sequence, test counts, Docker/Testcontainers status, commit list, and the MQ preparation explanation.

- [x] **Step 4: Commit documentation**

```powershell
git add docs/superpowers/specs docs/superpowers/plans/2026-07-14-order-idempotency.md
git commit -m "docs: record order idempotency implementation"
```

## Plan self-review result

- Every approved requirement maps to at least one task.
- Transaction rollback and duplicate-claim resolution are tested separately.
- A duplicate key thrown by order creation cannot be misclassified as an idempotency replay.
- Replay lookup always includes `userId`.
- The 24-hour contract works even if scheduled cleanup is delayed.
- Redis and MQ remain outside this implementation scope.
- Real MySQL concurrency evidence is distinguished from H2 and Mock-based evidence.
