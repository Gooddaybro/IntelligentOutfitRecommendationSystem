# Real Payment, Callback, Refund, and After-Sale Design

## Purpose

本文档定义 `Intelligent Outfit Recommendation System` 在现有订单、库存、Mock 支付基础上，如何演进到真实支付渠道、支付回调、退款和售后。

当前阶段先讨论并记录设计，不直接修改代码。实现时优先复用已经完成的订单、库存、支付接口和 Service 逻辑，避免重新做一套交易链路。

## Current State

### Already Implemented

- 订单创建：
  - `POST /api/orders`
  - `POST /api/orders/buy-now`
- 订单查询：
  - `GET /api/orders`
  - `GET /api/orders/{orderNo}`
- Mock 支付：
  - `POST /api/payments/mock-pay`
- 订单状态：
  - `UNPAID`
  - `PAID`
  - `CANCELLED`
  - `CLOSED`
- 库存状态流转：
  - 下单时 `available_stock -> locked_stock`
  - 支付成功时 `locked_stock -> sold_stock`
  - 订单取消或超时时 `locked_stock -> available_stock`

### Existing Reusable Core

现有 `PaymentService.mockPay(...)` 已经完成一条重要交易闭环：

1. 校验当前用户。
2. 锁定订单行。
3. 校验订单属于当前用户。
4. 校验订单状态是 `UNPAID`。
5. 确认库存售出。
6. 写入支付流水。
7. 更新订单状态为 `PAID`。
8. 重复支付已支付订单时返回已有成功流水。

这段逻辑应该成为真实支付后续扩展的核心复用点，而不是被支付宝、微信、Mock 支付各写一遍。

## Design Principles

### Java Backend Remains Transaction Source of Truth

前端、Python AI、第三方支付渠道都不能直接决定：

- 订单归属
- 订单金额
- 支付金额
- 支付状态
- 库存扣减
- 退款金额
- 售后状态

这些事实必须由 Java 后端从数据库读取并在事务中更新。

### Reuse Existing APIs Where Possible

继续复用：

- `POST /api/orders`
- `POST /api/orders/buy-now`
- `GET /api/orders`
- `GET /api/orders/{orderNo}`

真实支付不改变下单入口。购物车结算和立即购买仍然先生成 `UNPAID` 订单，并锁定库存。

支付模块只补充：

- 统一发起支付入口
- 渠道策略
- 回调验签
- 回调幂等
- 支付单查询
- 退款和售后单

### Callback Drives Final Payment Success

真实支付中，前端看到“支付完成”不能直接把订单改成 `PAID`。

订单最终成功必须来自：

1. 第三方支付回调到 Java。
2. Java 验签成功。
3. Java 根据支付单和订单状态做幂等校验。
4. Java 在事务中更新支付流水、订单状态和库存。

### Mock Payment Should Follow the Same Shape

第一阶段不急着直接接支付宝/微信，应该先把 Mock 支付改造成和真实支付同构：

```text
创建支付单 -> 调用支付策略 -> 策略返回结果 -> 统一确认支付成功 -> 更新订单和库存
```

这样后续增加真实渠道时，只新增策略实现和验签逻辑，不重写订单交易核心。

## Recommended Phasing

## Phase 1: Payment Strategy and Unified Payment Entry

### Goal

把现有 Mock 支付迁移到统一支付架构中，同时保留 `POST /api/payments/mock-pay` 兼容入口。

### New Public API

```http
POST /api/payments
Content-Type: application/json
Authorization: Bearer <access-token>

{
  "orderNo": "ORD...",
  "channel": "MOCK"
}
```

返回：

```json
{
  "paymentNo": "PAY...",
  "orderNo": "ORD...",
  "amount": 299.00,
  "channel": "MOCK",
  "status": "SUCCESS",
  "transactionId": "mock-provider-transaction-id",
  "paidAt": "2026-06-12T15:00:00"
}
```

### Compatibility API

保留：

```http
POST /api/payments/mock-pay
```

但内部改为调用统一支付入口：

```text
mockPay(userId, request)
-> pay(userId, new CreatePaymentRequest(orderNo, "MOCK"))
```

这样现有前端和测试不会断。

### Service Boundary

新增或调整为：

```text
PaymentService.pay(userId, CreatePaymentRequest)
PaymentService.confirmPaymentSuccess(command)
PaymentStrategy.pay(context)
PaymentStrategy.verifyCallback(callback)
PaymentStrategy.refund(context)
```

其中最重要的是：

```text
confirmPaymentSuccess(...)
```

统一负责：

1. 锁定支付单或订单。
2. 幂等判断。
3. 更新 payment 为 `SUCCESS`。
4. 更新 order 为 `PAID`。
5. 确认库存 `locked_stock -> sold_stock`。

Mock、支付宝、微信最终都必须走这个方法。

## Phase 2: Payment Callback Skeleton and Idempotency

### Goal

先建立真实渠道需要的回调骨架，即使第一版只接 Mock，也要把回调日志和幂等模型设计好。

### New API

```http
POST /api/payments/callback/{channel}
```

该接口需要从 Spring Security 中放行，但不能裸信任请求。

安全边界：

- `MOCK` 回调只能在测试环境或开发环境开放。
- `ALIPAY` / `WECHAT` 必须验签。
- 验签失败只记录日志，不更新订单或库存。

### Callback Log Table

新增：

```text
payment_callback_log
- id
- channel
- payment_no
- order_no
- provider_trade_no
- event_type
- raw_body
- headers
- signature_valid
- handled
- failure_reason
- created_at
```

用途：

- 记录第三方原始通知。
- 排查签名、金额、订单号不一致问题。
- 支持重复回调幂等分析。
- 避免第三方平台问题难以追踪。

### Idempotency Rules

支付回调可能重复发送，所以必须幂等：

```text
如果 payment.status == SUCCESS:
    直接返回渠道要求的 success 响应

如果 order.status == PAID:
    校验是否已有成功 payment
    有则返回 success
    没有则记录异常并拒绝更新

如果 amount != order.total_amount:
    记录 callback log
    拒绝更新

如果 payment/order/user/channel 不匹配:
    记录 callback log
    拒绝更新
```

## Phase 3: Real Payment Channel Integration

### Goal

在支付骨架稳定后，只接入一个真实渠道，建议先选一个：

- `ALIPAY`
- `WECHAT`

不要两个同时做。

### Strategy Implementations

```text
PaymentStrategy
├── MockPaymentStrategy
├── AlipayPaymentStrategy
└── WechatPayPaymentStrategy
```

统一接口：

```text
pay(context)
query(context)
verifyCallback(callback)
parseCallback(callback)
refund(context)
verifyRefundCallback(callback)
```

真实渠道接入前，需要根据官方文档确认：

- SDK 版本
- 应用 ID
- 商户号
- 私钥、公钥、证书配置
- 回调验签规则
- 支付请求参数
- 支付成功通知格式
- 退款通知格式

这些内容变化概率较高，开发前必须再查官方文档，不应只依赖旧记忆。

### Frontend Behavior

前端发起支付后，根据渠道响应处理：

- `MOCK`：直接得到成功支付结果。
- `ALIPAY`：可能打开支付 URL、二维码或表单。
- `WECHAT`：可能展示二维码或跳转小程序/Native 支付。

前端支付完成后只能轮询：

```text
GET /api/orders/{orderNo}
GET /api/payments/{paymentNo}
```

前端不能自己确认支付成功。

## Phase 4: Refund

### Goal

支付链路稳定后再做退款。退款不要混在 `payment` 表里，使用独立退款单。

### New Table

```text
refund_order
- id
- refund_no
- order_no
- payment_no
- user_id
- channel
- amount
- reason
- status
- provider_refund_no
- requested_at
- refunded_at
- created_at
- updated_at
```

建议状态：

```text
REQUESTED
PROCESSING
SUCCESS
FAILED
REJECTED
```

### New APIs

```http
POST /api/refunds
GET /api/refunds/{refundNo}
POST /api/refunds/callback/{channel}
```

第一版退款范围建议保守：

- 只支持整单退款。
- 只支持已支付订单。
- 不支持部分退款。
- 不支持换货。
- 不自动恢复可售库存，除非后续引入退货验收。

原因：服装场景下，退款和退货不是一回事。用户申请退款不代表商品已经退回，也不代表库存可以重新销售。

## Phase 5: After-Sale

### Goal

售后是退款之上的业务流程，建议后续单独做，不要第一版和支付一起完成。

### New Table

```text
after_sale_order
- id
- after_sale_no
- order_no
- user_id
- type
- reason
- status
- requested_at
- approved_at
- closed_at
- created_at
- updated_at
```

建议类型：

```text
REFUND_ONLY
RETURN_AND_REFUND
EXCHANGE
```

建议状态：

```text
REQUESTED
APPROVED
REJECTED
WAIT_RETURN
RETURNED
REFUNDED
CLOSED
```

第一版不建议做完整售后，因为它会引入：

- 人工审核
- 退货物流
- 质检
- 二次入库
- 换货订单
- 部分退款

这些复杂度明显高于支付接入本身。

## Suggested First Implementation Scope

第一阶段建议只做：

1. 新增 `PaymentStrategy` 抽象。
2. 新增 `MockPaymentStrategy`。
3. 新增统一支付请求 `CreatePaymentRequest`。
4. 新增统一支付接口 `POST /api/payments`。
5. 保留并复用 `POST /api/payments/mock-pay`。
6. 抽出 `confirmPaymentSuccess(...)`。
7. 新增支付单查询 `GET /api/payments/{paymentNo}`。
8. 新增回调日志表和回调 Controller 骨架。
9. Mock 回调或内部确认先走同一个幂等成功确认逻辑。
10. 前端支付入口从 `mock-pay` 逐步迁移到统一 `POST /api/payments`。

第一阶段暂不做：

- 真实支付宝 SDK。
- 真实微信支付 SDK。
- 退款。
- 售后。
- 部分退款。
- 退货入库。
- 多币种。
- 分账。

## Proposed Backend File Changes

预计新增：

```text
backend/src/main/java/.../payment/dto/CreatePaymentRequest.java
backend/src/main/java/.../payment/dto/PaymentCallbackResponse.java
backend/src/main/java/.../payment/model/PaymentCallbackLog.java
backend/src/main/java/.../payment/strategy/PaymentStrategy.java
backend/src/main/java/.../payment/strategy/PaymentStrategyRegistry.java
backend/src/main/java/.../payment/strategy/PaymentRequestContext.java
backend/src/main/java/.../payment/strategy/PaymentResult.java
backend/src/main/java/.../payment/strategy/MockPaymentStrategy.java
backend/src/main/resources/db/migration/V8__payment_strategy_callback_schema.sql
```

预计修改：

```text
backend/src/main/java/.../payment/api/PaymentController.java
backend/src/main/java/.../payment/service/PaymentService.java
backend/src/main/java/.../payment/mapper/PaymentMapper.java
backend/src/main/resources/mapper/payment/PaymentMapper.xml
backend/src/main/java/.../security/SecurityConfig.java
frontend/src/shared/api/client.ts
frontend/src/pages/OrdersPage.tsx
```

## Verification Plan

### Backend Tests

需要覆盖：

- `POST /api/payments` 可以用 `MOCK` 支付未支付订单。
- `POST /api/payments/mock-pay` 仍然兼容。
- 重复支付已支付订单返回已有成功流水，不重复扣库存。
- 已取消订单不能支付。
- 已超时关闭订单不能支付。
- 支付金额必须来自订单，不允许前端传入。
- 支付成功后订单状态为 `PAID`。
- 支付成功后库存从 `locked_stock` 转为 `sold_stock`。
- 回调重复到达时保持幂等。
- 回调金额不一致时拒绝更新。

### Frontend Tests

需要覆盖：

- 订单页调用统一支付接口。
- 支付成功后重新加载订单。
- 支付失败时展示错误。
- 前端不传支付金额。

### Manual Verification

1. 启动 MySQL。
2. 启动 Java 后端。
3. 启动前端。
4. 注册或登录用户。
5. 通过传统浏览或 AI 推荐加购商品。
6. 创建订单。
7. 使用统一支付接口支付。
8. 查看订单变为 `PAID`。
9. 检查库存锁定量减少、售出量增加。
10. 重复支付同一订单，确认不会重复扣库存。

## Open Decisions Before Implementation

### Decision 1: First Real Channel

真实渠道接入时需要选择一个先做：

- `ALIPAY`
- `WECHAT`

建议第一阶段不做真实渠道，只做 Mock 策略化和回调骨架。

### Decision 2: Refund Scope

退款第一版建议只支持整单退款，不支持部分退款。

### Decision 3: Stock Recovery

退款成功后是否恢复库存，需要结合售后类型：

- 仅退款：不恢复库存。
- 退货退款：退货验收后再恢复库存。

建议第一版退款不恢复库存，等售后模块接入后再做退货入库。

## Recommended Approval Scope

建议你先批准以下开发范围：

```text
支付一期：
统一支付入口
+ Mock 支付策略化
+ 支付成功幂等确认
+ 支付单查询
+ 回调日志表
+ Mock/真实渠道回调骨架
+ 前端订单页改用统一支付入口
```

暂缓：

```text
支付宝真实接入
微信真实接入
退款
售后
```

这样可以先把支付架构打稳，再选择真实渠道接入。
