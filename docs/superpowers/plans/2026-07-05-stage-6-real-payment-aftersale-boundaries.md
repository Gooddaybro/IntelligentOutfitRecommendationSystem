# Real Payment And After-Sale Boundaries Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add provider-shaped payment callbacks and explicit after-sale state without adding MQ or real payment credentials.

**Architecture:** Keep `PaymentService` as the only transaction state mutator. `ALIPAY` and `WECHAT` create pending payments, signed callbacks drive success through the existing stock/order/payment confirmation path, and after-sale requests are current-user records linked to paid orders.

**Tech Stack:** Java 21, Spring Boot, MyBatis, Flyway, HMAC-SHA256, JUnit 5, Mockito, MockMvc.

## Global Constraints

- Java remains the source of truth for order, payment, inventory, and after-sale facts.
- `MOCK` payment must stay deterministic for local demos.
- Provider callbacks are public but untrusted until signature and Java-owned payment facts are verified.
- Do not add MQ in Stage 6.
- Do not call real payment provider SDKs or require production credentials in CI.
- Do not add address book or shipping unless checkout starts requiring a destination.

---

## Files

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/dto/ProviderPaymentCallback.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentCallbackVerification.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentProviderProperties.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentCallbackVerifier.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/AlipayPaymentStrategy.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/WechatPaymentStrategy.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/mapper/PaymentMapper.java`
- Modify: `backend/src/main/resources/mapper/payment/PaymentMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/aftersale/**`
- Create: `backend/src/main/resources/mapper/aftersale/AfterSaleMapper.xml`
- Create: `backend/src/main/resources/db/migration/V15__payment_callback_after_sale_schema.sql`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/resources/application-test.properties`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentControllerTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aftersale/AfterSaleServiceTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/aftersale/AfterSaleControllerTests.java`

## Task 1: Provider Pending Payment And Signed Callback

**Interfaces:**
- Consumes: `PaymentStrategy.pay(PaymentRequestContext)`
- Produces: pending provider strategies, `PaymentService.handleCallback(String, String, HttpServletRequest)`

- [x] **Step 1: Write failing payment service tests**

Expected behavior:

- `ALIPAY` creates a `PENDING` payment and does not confirm stock.
- A valid signed success callback confirms stock and marks order paid.
- A duplicate success callback does not confirm stock again.
- An invalid signature records a callback log but does not mutate inventory or order.

- [x] **Step 2: Implement provider strategies, callback verifier, mapper updates, and service flow**

## Task 2: After-Sale Request API

**Interfaces:**
- Consumes: existing `OrderMapper` paid order lookup.
- Produces: current-user after-sale create/list/cancel API.

- [x] **Step 1: Write failing after-sale service and controller tests**

Expected behavior:

- Paid orders can create one `REQUESTED` after-sale request.
- Unpaid orders are rejected.
- Users cannot access or cancel another user's request.
- `REQUESTED` requests can be cancelled by the owner.

- [x] **Step 2: Implement migration, mapper, service, DTOs, controller**

## Task 3: Verification

- [x] **Step 1: Run focused backend tests**

```bash
cd backend
sh ./mvnw -q -Dtest=PaymentServiceTests,PaymentControllerTests,PaymentMapperTests,AfterSaleServiceTests,AfterSaleControllerTests test
```

- [x] **Step 2: Run backend full tests**

```bash
cd backend
sh ./mvnw -q test
```

- [x] **Step 3: Run root local check**

```bash
cd ..
sh scripts/check-local.sh
```

## Completion Criteria

- `MOCK` payment still returns immediate `SUCCESS`.
- `ALIPAY` and `WECHAT` create `PENDING` payment records without changing order or inventory state.
- Signed provider callbacks can mark a pending payment successful through the same idempotent success path.
- Duplicate callbacks do not double-confirm stock or double-mark orders.
- Invalid callbacks are audited but do not change transaction state.
- Users can create, list, and cancel after-sale requests for their own paid orders.
- Stage 6 does not introduce MQ, real provider SDKs, production secrets, address book, or shipping.
