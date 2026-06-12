# Payment Phase One Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a unified payment entry, strategy-based Mock payment, callback log skeleton, payment query API, and frontend migration while preserving the existing Mock payment behavior.

**Architecture:** Keep Java as the source of truth for payment, order, and inventory facts. `PaymentService` owns the transaction boundary and exposes one reusable success-confirmation path; channel-specific code lives behind `PaymentStrategy`. Existing order creation, order query, inventory confirmation, and `/api/payments/mock-pay` compatibility are reused.

**Tech Stack:** Java 21, Spring Boot, MyBatis, Flyway, JUnit/MockMvc/Mockito, React + TypeScript + Vite.

---

## File Structure

### Backend Files

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/CreatePaymentRequest.java`
  - Public API request for unified payment creation. It must not accept amount or user ID.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentCallbackResponse.java`
  - Minimal response contract for callback endpoints.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/PaymentCallbackLog.java`
  - Persistence model for raw callback audit data.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentStrategy.java`
  - Channel abstraction.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentStrategyRegistry.java`
  - Selects a strategy by channel and rejects unsupported channels.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentRequestContext.java`
  - Immutable context passed from service to strategy.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentResult.java`
  - Strategy result returned to service.
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/MockPaymentStrategy.java`
  - Mock channel implementation.
- Create: `backend/src/main/resources/db/migration/V8__payment_strategy_callback_schema.sql`
  - Extends payment to support pending records and creates `payment_callback_log`.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/api/PaymentController.java`
  - Add unified pay, payment query, and callback skeleton endpoints.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
  - Refactor Mock logic through strategy and reusable `confirmPaymentSuccess`.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/mapper/PaymentMapper.java`
  - Add payment query, pending insert, success update, and callback log methods.
- Modify: `backend/src/main/resources/mapper/payment/PaymentMapper.xml`
  - Add SQL for the new mapper methods with boundary comments on callback/idempotency queries.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/Payment.java`
  - Add provider-facing fields required by the new schema.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentResponse.java`
  - Keep the existing response stable; add only fields required by the frontend if needed.
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java`
  - Permit callback endpoint only; authenticated pay/query endpoints stay protected.
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentControllerTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentMapperTests.java`

### Frontend Files

- Modify: `frontend/src/shared/api/types.ts`
  - Add `CreatePaymentRequest` if useful and keep `PaymentResponse` aligned.
- Modify: `frontend/src/shared/api/client.ts`
  - Add `pay(orderNo, channel)` and `payment(paymentNo)`.
- Modify: `frontend/src/pages/OrdersPage.tsx`
  - Replace `api.payMock(orderNo)` with `api.pay(orderNo, "MOCK")`.

---

## Task 1: Schema, Model, and Mapper Contract

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__payment_strategy_callback_schema.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/PaymentCallbackLog.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/model/Payment.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/mapper/PaymentMapper.java`
- Modify: `backend/src/main/resources/mapper/payment/PaymentMapper.xml`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentMapperTests.java`

- [ ] **Step 1: Write the failing mapper tests**

Add tests that insert a pending payment, find it by payment number, update it to success, and insert a callback log:

```java
@Test
void insertsPendingPaymentThenUpdatesItToSuccess() {
    SalesOrder order = createPaidCandidateOrder();
    Payment payment = payment(order, "PAYPENDING1", "PENDING");

    paymentMapper.insertPayment(payment);
    Payment found = paymentMapper.findByPaymentNo("PAYPENDING1");

    assertThat(found.getStatus()).isEqualTo("PENDING");
    assertThat(found.getAmount()).isEqualByComparingTo(order.getTotalAmount());

    int updated = paymentMapper.markPaymentSuccess(
            "PAYPENDING1",
            "mock-provider-001",
            "mock-transaction-001",
            LocalDateTime.now()
    );

    assertThat(updated).isEqualTo(1);
    assertThat(paymentMapper.findByPaymentNo("PAYPENDING1").getStatus()).isEqualTo("SUCCESS");
}

@Test
void recordsCallbackLogForAuditAndIdempotencyAnalysis() {
    PaymentCallbackLog log = new PaymentCallbackLog();
    log.setChannel("MOCK");
    log.setPaymentNo("PAYCALLBACK1");
    log.setOrderNo("ORDCALLBACK1");
    log.setProviderTradeNo("mock-provider-001");
    log.setEventType("PAYMENT_SUCCESS");
    log.setRawBody("{\"paymentNo\":\"PAYCALLBACK1\"}");
    log.setHeaders("{\"x-mock-signature\":\"valid\"}");
    log.setSignatureValid(true);
    log.setHandled(false);

    paymentMapper.insertCallbackLog(log);

    assertThat(log.getId()).isPositive();
}
```

- [ ] **Step 2: Run mapper tests and verify they fail**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentMapperTests test
```

Expected: fail because `PaymentCallbackLog`, `findByPaymentNo`, `markPaymentSuccess`, and `insertCallbackLog` do not exist.

- [ ] **Step 3: Add schema migration**

Create `V8__payment_strategy_callback_schema.sql`:

```sql
ALTER TABLE payment
    MODIFY COLUMN transaction_id VARCHAR(128) NULL;

ALTER TABLE payment
    MODIFY COLUMN paid_at DATETIME(6) NULL;

ALTER TABLE payment
    ADD COLUMN provider_trade_no VARCHAR(128) NULL;

ALTER TABLE payment
    ADD COLUMN provider_payload TEXT NULL;

CREATE TABLE payment_callback_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    channel VARCHAR(32) NOT NULL,
    payment_no VARCHAR(64) NULL,
    order_no VARCHAR(64) NULL,
    provider_trade_no VARCHAR(128) NULL,
    event_type VARCHAR(64) NOT NULL,
    raw_body TEXT NOT NULL,
    headers TEXT NULL,
    signature_valid BOOLEAN NOT NULL,
    handled BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_payment_callback_payment_no (payment_no),
    KEY idx_payment_callback_order_no (order_no),
    KEY idx_payment_callback_provider_trade_no (provider_trade_no),
    KEY idx_payment_callback_created_at (created_at)
);
```

- [ ] **Step 4: Add `PaymentCallbackLog` model with class Javadoc**

The class-level Javadoc must explain that this model is an audit boundary and does not authorize payment state changes by itself.

```java
/**
 * Payment callback audit record.
 *
 * <p>This model stores raw provider callback facts for troubleshooting and idempotency analysis.
 * A saved callback log never means the payment is trusted; the service must still verify the
 * channel signature, amount, order ownership, and payment state before changing order or stock.</p>
 */
@Data
public class PaymentCallbackLog {
    private Long id;
    private String channel;
    private String paymentNo;
    private String orderNo;
    private String providerTradeNo;
    private String eventType;
    private String rawBody;
    private String headers;
    private Boolean signatureValid;
    private Boolean handled;
    private String failureReason;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 5: Extend mapper interface and XML**

Add methods:

```java
Payment findByPaymentNo(@Param("paymentNo") String paymentNo);

int markPaymentSuccess(
        @Param("paymentNo") String paymentNo,
        @Param("providerTradeNo") String providerTradeNo,
        @Param("transactionId") String transactionId,
        @Param("paidAt") LocalDateTime paidAt
);

void insertCallbackLog(PaymentCallbackLog log);
```

Add XML SQL. Keep a Mapper XML comment above `markPaymentSuccess` explaining the idempotency boundary:

```xml
<!--
    Payment success is updated by paymentNo and only from non-success states.
    This guards repeated provider callbacks from creating a second stock confirmation path.
-->
<update id="markPaymentSuccess">
    UPDATE payment
    SET status = 'SUCCESS',
        provider_trade_no = #{providerTradeNo},
        transaction_id = #{transactionId},
        paid_at = #{paidAt},
        updated_at = CURRENT_TIMESTAMP(6)
    WHERE payment_no = #{paymentNo}
      AND status != 'SUCCESS'
</update>
```

- [ ] **Step 6: Run mapper tests and verify they pass**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentMapperTests test
```

Expected: PASS.

---

## Task 2: Payment Strategy Abstraction

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentStrategy.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentStrategyRegistry.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentRequestContext.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/PaymentResult.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/MockPaymentStrategy.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`

- [ ] **Step 1: Write failing strategy registry tests**

Add tests that validate supported and unsupported channels:

```java
@Test
void strategyRegistryReturnsMockStrategyByChannel() {
    PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(new MockPaymentStrategy()));

    assertThat(registry.getRequired("MOCK").channel()).isEqualTo("MOCK");
}

@Test
void strategyRegistryRejectsUnsupportedChannel() {
    PaymentStrategyRegistry registry = new PaymentStrategyRegistry(List.of(new MockPaymentStrategy()));

    assertThatThrownBy(() -> registry.getRequired("ALIPAY"))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("unsupported payment channel: ALIPAY");
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentServiceTests test
```

Expected: compile failure because strategy classes do not exist.

- [ ] **Step 3: Add strategy contracts with Javadocs**

Add `PaymentStrategy`:

```java
/**
 * Channel-specific payment boundary.
 *
 * <p>Implementations may call an external provider, build a redirect payload, or simulate a payment,
 * but they must not update orders or inventory directly. State changes remain in {@code PaymentService}
 * so all channels share the same idempotent transaction path.</p>
 */
public interface PaymentStrategy {
    String channel();

    PaymentResult pay(PaymentRequestContext context);
}
```

Add `PaymentRequestContext`:

```java
/**
 * Immutable payment facts passed to a channel strategy.
 *
 * <p>The amount and order ownership are loaded by Java from the database before this object is created.
 * Frontend requests never provide amount or user ID.</p>
 */
public record PaymentRequestContext(
        String paymentNo,
        String orderNo,
        Long userId,
        BigDecimal amount,
        String channel
) {
}
```

Add `PaymentResult`:

```java
/**
 * Provider result returned by a payment strategy.
 *
 * <p>For Mock payments this can be immediately successful. For real providers the first version may
 * return a pending result and rely on a verified callback to confirm success.</p>
 */
public record PaymentResult(
        String paymentNo,
        String channel,
        String status,
        String providerTradeNo,
        String transactionId,
        String providerPayload,
        LocalDateTime paidAt
) {
}
```

Add `MockPaymentStrategy`:

```java
/**
 * Development payment strategy that simulates a provider success result.
 *
 * <p>This class keeps Mock behavior behind the same channel boundary that real providers will use,
 * allowing tests and frontend flows to exercise the production transaction shape without external credentials.</p>
 */
@Component
public class MockPaymentStrategy implements PaymentStrategy {
    @Override
    public String channel() {
        return "MOCK";
    }

    @Override
    public PaymentResult pay(PaymentRequestContext context) {
        LocalDateTime paidAt = LocalDateTime.now();
        String transactionId = UUID.randomUUID().toString();
        return new PaymentResult(
                context.paymentNo(),
                context.channel(),
                "SUCCESS",
                "MOCK-" + transactionId,
                transactionId,
                "{\"channel\":\"MOCK\"}",
                paidAt
        );
    }
}
```

- [ ] **Step 4: Add registry**

```java
/**
 * Resolves payment strategies by channel.
 *
 * <p>Unsupported channels are rejected before any order or inventory state changes, which keeps the
 * public payment API extensible without allowing arbitrary channel names from the frontend.</p>
 */
@Component
public class PaymentStrategyRegistry {
    private final Map<String, PaymentStrategy> strategies;

    public PaymentStrategyRegistry(List<PaymentStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(PaymentStrategy::channel, Function.identity()));
    }

    public PaymentStrategy getRequired(String channel) {
        PaymentStrategy strategy = strategies.get(channel);
        if (strategy == null) {
            throw new BadRequestException("unsupported payment channel: " + channel);
        }
        return strategy;
    }
}
```

- [ ] **Step 5: Run strategy tests and verify they pass**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentServiceTests test
```

Expected: PASS for the new registry tests; existing service tests may still fail until Task 3 if constructor dependencies change.

---

## Task 3: PaymentService Unified Pay and Idempotent Success Confirmation

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/CreatePaymentRequest.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentResponse.java` if provider fields need exposure
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`

- [ ] **Step 1: Add failing service tests for unified pay**

Add tests:

```java
@Test
void payWithMockChannelUsesUnifiedPathAndMarksOrderPaid() {
    SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "UNPAID");
    when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
    when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
    when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);
    when(orderMapper.updateOrderPaid(eq(88L), any(LocalDateTime.class))).thenReturn(1);

    PaymentResponse response = service.pay(10L, new CreatePaymentRequest("ORDPAY1", "MOCK"));

    verify(paymentMapper).insertPayment(any(Payment.class));
    verify(paymentMapper).markPaymentSuccess(
            eq(response.paymentNo()),
            any(String.class),
            any(String.class),
            any(LocalDateTime.class)
    );
    assertThat(response.channel()).isEqualTo("MOCK");
    assertThat(response.status()).isEqualTo("SUCCESS");
}

@Test
void mockPayDelegatesToUnifiedPayPath() {
    SalesOrder order = salesOrder(88L, 10L, "ORDPAY1", "UNPAID");
    when(orderMapper.findOrderByUserIdAndOrderNoForUpdate(10L, "ORDPAY1")).thenReturn(order);
    when(orderMapper.findItemsByOrderId(88L)).thenReturn(List.of(orderItem(2102L, 2)));
    when(inventoryMapper.confirmSoldStock(2102L, 2)).thenReturn(1);
    when(orderMapper.updateOrderPaid(eq(88L), any(LocalDateTime.class))).thenReturn(1);

    PaymentResponse response = service.mockPay(10L, new MockPaymentRequest("ORDPAY1"));

    assertThat(response.channel()).isEqualTo("MOCK");
    verify(paymentMapper).insertPayment(any(Payment.class));
}
```

- [ ] **Step 2: Run service tests and verify they fail**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentServiceTests test
```

Expected: fail because `CreatePaymentRequest`, `pay`, and `markPaymentSuccess` integration do not exist.

- [ ] **Step 3: Add `CreatePaymentRequest` with Javadoc**

```java
/**
 * Unified payment creation request.
 *
 * <p>The frontend may choose an order and a channel, but it cannot submit amount, user ID, payment
 * status, or provider trade numbers. Those facts are loaded or generated by the backend transaction.</p>
 */
public record CreatePaymentRequest(
        @NotBlank String orderNo,
        @NotBlank String channel
) {
}
```

- [ ] **Step 4: Refactor `PaymentService`**

Implement:

```java
@Transactional
public PaymentResponse pay(Long userId, CreatePaymentRequest request) {
    validateUserId(userId);
    String orderNo = normalizeOrderNo(request.orderNo());
    String channel = normalizeChannel(request.channel());
    PaymentStrategy strategy = paymentStrategyRegistry.getRequired(channel);

    SalesOrder order = orderMapper.findOrderByUserIdAndOrderNoForUpdate(userId, orderNo);
    if (order == null) {
        throw new ResourceNotFoundException("order not found: " + orderNo);
    }
    if (PAID_STATUS.equals(order.getStatus())) {
        return toResponse(findExistingSuccessPayment(order));
    }
    validatePayable(order);

    Payment payment = createPendingPayment(order, channel);
    paymentMapper.insertPayment(payment);

    PaymentResult result = strategy.pay(new PaymentRequestContext(
            payment.getPaymentNo(),
            order.getOrderNo(),
            order.getUserId(),
            order.getTotalAmount(),
            channel
    ));

    if (SUCCESS_STATUS.equals(result.status())) {
        return confirmPaymentSuccess(order, payment, result);
    }
    return toResponse(payment);
}

@Transactional
public PaymentResponse mockPay(Long userId, MockPaymentRequest request) {
    return pay(userId, new CreatePaymentRequest(normalizeOrderNo(request), MOCK_CHANNEL));
}
```

Extract:

```java
private PaymentResponse confirmPaymentSuccess(SalesOrder order, Payment payment, PaymentResult result) {
    confirmSoldStock(order);
    LocalDateTime paidAt = result.paidAt() == null ? LocalDateTime.now() : result.paidAt();
    paymentMapper.markPaymentSuccess(
            payment.getPaymentNo(),
            result.providerTradeNo(),
            result.transactionId(),
            paidAt
    );
    markOrderPaid(order, paidAt);

    payment.setStatus(SUCCESS_STATUS);
    payment.setProviderTradeNo(result.providerTradeNo());
    payment.setTransactionId(result.transactionId());
    payment.setPaidAt(paidAt);
    return toResponse(payment);
}
```

Keep behavior:

- Paid order returns existing success payment.
- Cancelled or closed order rejects payment.
- Non-`UNPAID` order rejects payment.
- Amount comes from `SalesOrder.totalAmount`.

- [ ] **Step 5: Run service tests**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentServiceTests test
```

Expected: PASS.

---

## Task 4: Controller APIs and Security Boundary

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/PaymentCallbackResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/api/PaymentController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentControllerTests.java`

- [ ] **Step 1: Write failing controller tests**

Add tests:

```java
@Test
void unifiedPayCurrentUsersOrderWithMockChannel() throws Exception {
    String accessToken = registerAndLogin(nextUsername());
    resetInventory(2005, 5);
    addCartItem(accessToken, 2005, 1);
    String orderNo = createOrder(accessToken, 2005);

    mockMvc.perform(post("/api/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "orderNo": "%s",
                              "channel": "MOCK"
                            }
                            """.formatted(orderNo)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderNo").value(orderNo))
            .andExpect(jsonPath("$.data.channel").value("MOCK"))
            .andExpect(jsonPath("$.data.status").value("SUCCESS"));
}

@Test
void callbackEndpointIsPublicButDoesNotTrustPayload() throws Exception {
    mockMvc.perform(post("/api/payments/callback/MOCK")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"paymentNo\":\"PAY_UNKNOWN\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.received").value(true));
}
```

- [ ] **Step 2: Run controller tests and verify they fail**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentControllerTests test
```

Expected: fail because `/api/payments` and callback endpoint do not exist.

- [ ] **Step 3: Add callback response DTO**

```java
/**
 * Response returned to payment callback senders.
 *
 * <p>This response only acknowledges receipt by the Java backend. It does not expose order ownership,
 * payment amount, or whether a state transition occurred.</p>
 */
public record PaymentCallbackResponse(
        boolean received,
        String channel
) {
}
```

- [ ] **Step 4: Add controller endpoints**

Add:

```java
@PostMapping
public ApiResponse<PaymentResponse> pay(
        Authentication authentication,
        @Valid @RequestBody CreatePaymentRequest request
) {
    CurrentUser currentUser = CurrentUser.from(authentication);
    return ApiResponse.ok(paymentService.pay(currentUser.userId(), request));
}

@GetMapping("/{paymentNo}")
public ApiResponse<PaymentResponse> getPayment(
        Authentication authentication,
        @PathVariable String paymentNo
) {
    CurrentUser currentUser = CurrentUser.from(authentication);
    return ApiResponse.ok(paymentService.getPayment(currentUser.userId(), paymentNo));
}

@PostMapping("/callback/{channel}")
public ApiResponse<PaymentCallbackResponse> callback(
        @PathVariable String channel,
        @RequestBody(required = false) String rawBody,
        HttpServletRequest request
) {
    paymentService.recordCallback(channel, rawBody, request);
    return ApiResponse.ok(new PaymentCallbackResponse(true, channel));
}
```

The callback method needs Javadoc or class-level controller comment update explaining why this endpoint is public but still untrusted until strategy verification.

- [ ] **Step 5: Permit callback endpoint only**

In `SecurityConfig`, add:

```java
.requestMatchers(HttpMethod.POST, "/api/payments/callback/**").permitAll()
```

Do not permit:

```text
POST /api/payments
GET /api/payments/{paymentNo}
POST /api/payments/mock-pay
```

- [ ] **Step 6: Run controller tests**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentControllerTests test
```

Expected: PASS.

---

## Task 5: Frontend Unified Payment Call

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/pages/OrdersPage.tsx`
- Test: `frontend/src/features/commerce-action/commerceActions.test.ts` if frontend test coverage is expanded

- [ ] **Step 1: Add frontend API method**

In `client.ts`, replace the order page dependency on `payMock` with:

```ts
pay: (orderNo: string, channel: "MOCK" | "ALIPAY" | "WECHAT" = "MOCK") =>
  requestJson<PaymentResponse>("/api/payments", {
    method: "POST",
    body: JSON.stringify({ orderNo, channel })
  }),
payment: (paymentNo: string) => requestJson<PaymentResponse>(`/api/payments/${paymentNo}`),
payMock: (orderNo: string) =>
  requestJson<PaymentResponse>("/api/payments/mock-pay", {
    method: "POST",
    body: JSON.stringify({ orderNo })
  })
```

Keep `payMock` for compatibility until the backend compatibility endpoint is intentionally removed.

- [ ] **Step 2: Update OrdersPage**

Change:

```ts
await api.payMock(orderNo);
```

to:

```ts
await api.pay(orderNo, "MOCK");
```

Keep button text as `Mock 支付` until real channel selection UI is added.

- [ ] **Step 3: Run frontend tests**

Run:

```powershell
cd frontend
npm test -- --run
```

Expected: PASS.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd frontend
npm run build
```

Expected: PASS.

---

## Task 6: Full Verification

**Files:**
- No new files.

- [ ] **Step 1: Run backend targeted payment tests**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=PaymentServiceTests,PaymentMapperTests,PaymentControllerTests test
```

Expected: all payment tests pass.

- [ ] **Step 2: Run full backend verification**

Run:

```powershell
cd backend
.\mvnw.cmd verify
```

Expected:

```text
BUILD SUCCESS
You have 0 Checkstyle violations.
```

- [ ] **Step 3: Run frontend tests and build**

Run:

```powershell
cd frontend
npm test -- --run
npm run build
```

Expected: tests and build pass.

- [ ] **Step 4: Run whitespace check**

Run:

```powershell
git diff --check
```

Expected: no whitespace errors. Windows line-ending warnings are acceptable if no error lines are reported.

- [ ] **Step 5: Manual smoke test**

Run the app stack:

```powershell
docker compose up -d
cd backend
.\mvnw.cmd spring-boot:run
```

In another terminal:

```powershell
cd frontend
npm run dev
```

Manual path:

1. Register or log in.
2. Add a product to cart from traditional browsing or AI recommendations.
3. Create an order.
4. Click `Mock 支付`.
5. Confirm the order becomes `PAID`.
6. Repeat payment on the same order by API and verify the same success payment is returned without duplicate stock movement.

---

## Scope Guardrails

Do not implement in this phase:

- Alipay SDK integration.
- WeChat Pay SDK integration.
- Real provider signature verification.
- Refunds.
- After-sale workflows.
- Partial refunds.
- Return-to-stock after refunds.
- Frontend real-channel QR code or redirect UI.

This phase only prepares the payment architecture so real channels can be added later without rewriting order and inventory logic.

## Self-Review

- Spec coverage: Covers unified payment entry, Mock strategy, existing endpoint compatibility, callback skeleton, payment query, frontend migration, and verification.
- Placeholder scan: No `TBD`, vague TODO items, or unresolved implementation placeholders.
- Type consistency: `CreatePaymentRequest`, `PaymentStrategy`, `PaymentRequestContext`, `PaymentResult`, and `PaymentCallbackResponse` are used consistently across tasks.
