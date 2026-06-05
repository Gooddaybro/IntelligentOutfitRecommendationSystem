# 后续商业与 AI 功能开发设计

## 目的

本文用于梳理当前项目后续四个未完成方向，并记录它们已经做到什么程度、下一步应该做到什么程度、实现边界和推荐开发顺序。

四个方向分别是：

- `BUY_NOW` 立即购买。
- 真实支付渠道、支付回调、退款和售后。
- SSE / WebSocket 流式 AI 返回。
- MQ 异步推荐任务。

第一阶段优先实现 `BUY_NOW`。该功能依赖现有购物车结算、订单、库存锁定、订单取消、超时关闭和 mock 支付能力，
目标是在不污染购物车状态的前提下，让用户可以直接基于单个 SKU 创建待支付订单。

## 总体优先级

推荐开发顺序：

1. `BUY_NOW` 立即购买。
2. SSE 流式 AI 返回。
3. 真实支付渠道、支付回调、退款和售后。
4. MQ 异步推荐任务。

排序理由：

- `BUY_NOW` 是当前订单闭环中的最小增量，已有订单和库存能力可以直接复用。
- SSE 能明显改善 AI 对话体验，但需要 Python 侧也提供流式 API，否则 Java 侧只能做伪流式。
- 真实支付涉及外部渠道、签名验签、异步回调和退款状态机，业务价值高但范围大。
- MQ 会引入新中间件和运维成本，短期可以先用任务表和定时任务替代。

## 当前状态总览

### BUY_NOW 立即购买

当前状态：

- 已有 `POST /api/orders` 从当前用户购物车选中 SKU 创建 `UNPAID` 订单。
- 已有 `OrderService#createOrder` 从购物车查询商品、SKU、价格和数量事实。
- 已有库存锁定逻辑，将 `available_stock` 转为 `locked_stock`。
- 已有订单取消和超时关闭逻辑，可以释放锁定库存。
- 已有 mock 支付逻辑，可以将 `locked_stock` 转为 `sold_stock`。
- 当前仍拒绝 `BUY_NOW`，因为 `CreateOrderRequest` 只支持 `source=CART`。

下一阶段目标：

- 新增单 SKU 立即购买接口。
- 请求体只接受 `skuId` 和 `quantity`。
- 后端重新查询 SKU、SPU、价格、商品状态和库存事实。
- 创建 `UNPAID` 订单并锁定库存。
- 不读取、不写入、不清理购物车。
- 购物车结算和立即购买共用订单创建核心逻辑。

暂不实现：

- 多 SKU 套装立即购买。
- 收货地址快照。
- 优惠券、运费、发票。
- 下单后自动支付。
- AI 代客下单。

### 真实支付渠道、支付回调、退款和售后

当前状态：

- 已有 `POST /api/payments/mock-pay` mock 支付。
- 已有 `payment` 表记录支付流水。
- mock 支付已经支持已支付订单的幂等返回。
- 支付成功时已经将锁定库存确认成售出库存。
- 当前没有支付宝、微信支付、真实支付发起、真实回调、退款单或售后单。

下一阶段目标：

- 定义统一 `PaymentStrategy` 边界，隔离 mock、支付宝、微信等渠道差异。
- 新增真实支付发起流程，记录待支付流水或支付单。
- 新增支付回调接口，并在 Spring Security 中放行指定 webhook 路径。
- 回调必须验签，并通过支付流水、渠道交易号和订单状态做幂等。
- 支付成功回调后，再确认库存售出并更新订单为 `PAID`。
- 后续再新增退款单表、退款发起、退款回调和售后状态。

暂不在 `BUY_NOW` 阶段实现：

- 外部支付 SDK 或 API 集成。
- 真实 webhook 验签。
- 退款、退货、售后。
- 部分退款或拆单退款。

### SSE / WebSocket 流式 AI 返回

当前状态：

- Java `AssistantService#chat` 同步调用 Python `/chat`。
- Java 会先保存用户消息，再等待 Python 完整响应，最后保存 assistant 消息。
- 当前同步链路可能因为 AI 生成慢导致前端等待时间长。
- 项目使用 Spring Web MVC，可以直接使用 Spring 内置 `SseEmitter`。
- 当前没有 Java SSE endpoint，也没有 Python 流式 API 客户端。

下一阶段目标：

- 优先实现 SSE，而不是 WebSocket。
- 新增流式聊天接口，例如 `POST /api/assistant/chat/stream`。
- Controller 返回 `SseEmitter`。
- Java 异步线程调用 Python 流式接口。
- Java 每收到 Python chunk，就通过 `SseEmitter#send` 推送给前端。
- 流结束后，拼接完整 assistant 内容并落库。

暂不实现：

- 多人实时聊天。
- 客户端高频双向消息。
- WebSocket 房间、广播、在线状态。
- 没有 Python 流式能力时的伪流式实现。

### MQ 异步推荐任务

当前状态：

- `pom.xml` 当前没有 RabbitMQ、RocketMQ 或 Kafka 依赖。
- `docker-compose.yml` 当前只服务本地 MySQL 等基础环境，没有 MQ 容器。
- 用户偏好更新后，目前没有异步重新生成推荐集的任务模型。
- 项目已经有 `@Scheduled` 用于未支付订单超时关闭，说明短期可以接受单体定时任务模式。

下一阶段目标：

- 短期采用“任务表 + `@Scheduled` 扫描”的轻量方案。
- 用户注册、身体数据更新或穿衣偏好更新后，插入推荐任务。
- 定时任务批量拉取待处理任务，调用 Python 推荐服务，保存推荐结果并更新任务状态。
- 中长期再引入 RabbitMQ 或 RocketMQ。

暂不在当前阶段实现：

- MQ 中间件接入。
- 分布式消费者。
- 延迟队列。
- 死信队列和复杂重试策略。

## BUY_NOW 详细设计

## 目标

`BUY_NOW` 允许当前登录用户绕过购物车，直接选择一个 SKU 和购买数量创建 `UNPAID` 订单。

核心原则：

- 不信任前端传入价格、金额、用户 ID 或订单状态。
- 不污染购物车状态。
- 不复用“先加入购物车再结算再删除”的间接流程。
- 与购物车结算共用同一套订单创建核心逻辑。
- 第一版只支持单 SKU。

## 非目标

第一版不实现：

- 一次购买多个 SKU。
- 推荐套装一键购买。
- 收货地址、地址快照、配送信息。
- 优惠券、运费、积分、发票。
- 订单来源字段持久化。
- 下单后自动支付。
- Python AI 直接创建订单。

## API 设计

新增接口：

```http
POST /api/orders/buy-now
Authorization: Bearer <accessToken>
Content-Type: application/json
```

请求体：

```json
{
  "skuId": 2103,
  "quantity": 1
}
```

响应沿用现有 `OrderResponse`：

```json
{
  "success": true,
  "data": {
    "orderNo": "ORD20260603120000123456",
    "status": "UNPAID",
    "totalAmount": 199.00,
    "items": [
      {
        "skuId": 2103,
        "spuId": 101,
        "skuCode": "SKU-2103",
        "spuCode": "SPU-101",
        "productName": "示例商品",
        "categoryName": "上衣",
        "color": "黑色",
        "size": "M",
        "salePrice": 199.00,
        "quantity": 1,
        "lineAmount": 199.00,
        "mainImageUrl": "https://example.com/image.jpg"
      }
    ],
    "createdAt": "2026-06-03T12:00:00.123",
    "paidAt": null,
    "closedAt": null,
    "closeReason": null
  },
  "error": null
}
```

请求规则：

- 必须登录。
- `skuId` 必须为正数。
- `quantity` 必须为正数。
- 请求体不能包含价格、总金额、用户 ID、订单状态。
- SKU 不存在时返回 `404 not_found`。
- SKU 或 SPU 非上架状态时返回 `400 bad_request`。
- 库存不足时返回 `400 bad_request`，整个事务回滚。

## DTO 设计

新增 `BuyNowRequest`：

```java
public record BuyNowRequest(
        @NotNull
        @Positive
        Long skuId,

        @NotNull
        @Positive
        Integer quantity
) {
}
```

设计说明：

- 第一版不使用 `List` 预留多 SKU。
- 未来如果做穿搭套装购买，应新增专门接口或请求模型，而不是让单 SKU 接口承担不同业务语义。
- 请求中不包含金额，避免前端篡改价格。
- 请求中不包含用户 ID，用户边界只来自 JWT。

## Controller 设计

修改 `OrderController`：

- 新增 `POST /api/orders/buy-now`。
- 从 `Authentication` 解析当前用户。
- 使用 `@Valid @RequestBody BuyNowRequest` 校验请求。
- 调用 `orderService.buyNow(currentUser.userId(), request)`。
- 返回现有 `ApiResponse<OrderResponse>`。

Controller 不负责：

- 查询 SKU。
- 计算价格。
- 判断库存。
- 操作购物车。

## Service 设计

修改 `OrderService`。

### 公共订单创建核心

从现有 `createOrder` 中抽出私有方法：

```text
createUnpaidOrderFromCheckoutItems(Long userId, List<OrderCheckoutItem> checkoutItems)
```

职责：

- 校验当前用户 ID。
- 校验结算项非空。
- 对每个结算项执行 `validateCheckoutItem`。
- 基于数据库价格和数量计算 `lineAmount` 和 `totalAmount`。
- 调用 `lockStock` 锁定库存。
- 创建 `SalesOrder`，状态为 `UNPAID`。
- 插入 `order_item` 快照。
- 返回 `OrderResponse`。

该方法不负责：

- 查询购物车。
- 查询直接购买 SKU。
- 清理购物车。
- 识别订单来源。
- 处理支付。

### 购物车结算改造

保留现有 `createOrder(Long userId, CreateOrderRequest request)` 对外语义：

1. 校验 `source=CART` 和 `skuIds`。
2. 根据当前用户和 SKU 集合查询购物车结算项。
3. 如果部分 SKU 不属于当前用户购物车，返回 `404 not_found`。
4. 调用 `createUnpaidOrderFromCheckoutItems` 创建订单。
5. 调用 `cartService.removePurchasedItems` 清理已购买购物车项。
6. 返回订单响应。

### 立即购买新增方法

新增：

```text
buyNow(Long userId, BuyNowRequest request)
```

流程：

1. 校验当前用户 ID。
2. 校验请求体不为空。
3. 查询 `orderMapper.findCheckoutItemBySkuId(request.skuId())`。
4. 如果查不到，抛出 `ResourceNotFoundException`。
5. 将查询得到的 `OrderCheckoutItem` 数量设置为 `request.quantity()`。
6. 调用 `createUnpaidOrderFromCheckoutItems` 创建订单。
7. 返回订单响应。

该方法不会调用任何购物车服务。

## Mapper 设计

修改 `OrderMapper`：

```java
OrderCheckoutItem findCheckoutItemBySkuId(@Param("skuId") Long skuId);
```

修改 `OrderMapper.xml`：

- 新增基于 SKU 查询结算快照的 SQL。
- 查询关联 `product_sku`、`product_spu`、`category`、`color`、`size_option` 和 `inventory`。
- 不关联 `cart_item`。
- 数量不从 SQL 决定，由 Java 使用请求中的 `quantity` 显式设置。
- 价格仍来自 `product_sku.sale_price`。
- 商品状态仍来自 `product_sku.status` 和 `product_spu.status`。
- 可用库存可继续作为查询快照字段返回，但最终防超卖依赖 `InventoryMapper#lockStock` 的原子更新。

SQL 语义：

```text
根据 skuId 查询当前可售商品事实，用于后端重算立即购买订单金额和生成订单明细快照。
```

## 事务边界

`buyNow` 和 `createOrder` 都应保持 `@Transactional`。

立即购买事务内完成：

1. 查询 SKU 结算快照。
2. 设置购买数量。
3. 校验商品状态和数量。
4. 计算金额。
5. 原子锁定库存。
6. 写订单主表。
7. 写订单明细。

如果任一步失败，事务回滚。库存锁定、订单主表和订单明细必须保持一致。

## 并发与一致性

关键保护：

- 库存防超卖仍依赖 `InventoryMapper#lockStock` 的条件更新。
- 前端传入的 quantity 只表达购买意图，不代表库存已经可用。
- 即使两个用户同时立即购买同一 SKU，也只有库存充足的一方能锁定成功。
- 立即购买不操作购物车，因此不会影响用户已有购物车内容。
- 后续支付、取消、超时关闭继续复用现有订单状态和库存流转。

## 错误处理

预期错误：

- `400 bad_request`：请求体为空、`skuId` 或 `quantity` 非法。
- `400 bad_request`：SKU 或 SPU 不可售。
- `400 bad_request`：库存不足。
- `404 not_found`：SKU 不存在。
- `401 unauthorized`：未登录访问。

沿用现有全局异常处理和 `ApiResponse` 错误格式。

## 测试计划

### Mapper 测试

新增或扩展 `OrderMapperTests`：

- `findCheckoutItemBySkuId` 可以查到存在 SKU 的商品快照。
- 返回字段包含 SKU、SPU、价格、商品名、分类、颜色、尺码、图片、状态和库存。
- 不存在 SKU 返回 `null`。
- 非上架 SKU 或 SPU 仍可返回状态，由 Service 统一拒绝。

### Service 测试

新增或扩展 `OrderServiceTests`：

- `buyNow` 可以创建 `UNPAID` 订单。
- `buyNow` 使用请求数量计算行金额和总金额。
- `buyNow` 锁定库存，`available_stock` 减少，`locked_stock` 增加。
- `buyNow` 不改变当前用户购物车。
- `buyNow` 拒绝不存在 SKU。
- `buyNow` 拒绝非上架 SKU 或 SPU。
- `buyNow` 库存不足时回滚，不创建订单和订单明细。
- 购物车结算改造后仍能创建订单并清理购物车。

### Controller 测试

新增或扩展 `OrderControllerTests`：

- `POST /api/orders/buy-now` 需要登录。
- 合法请求返回订单响应。
- 缺少 `skuId` 返回参数校验错误。
- 缺少 `quantity` 返回参数校验错误。
- 负数或零数量返回参数校验错误。
- 响应中不需要前端传入金额即可返回后端计算金额。

### 完整验证

实现完成后必须执行：

```powershell
.\mvnw.cmd verify
```

该项目 CI 会运行 Maven `verify`，Checkstyle 违规会导致构建失败。

## 手动验证计划

使用 Reqable、Postman 或本地 HTTP Client：

1. 登录普通用户，获取 Access Token。
2. 请求 `POST /api/orders/buy-now`，传入存在且库存充足的 `skuId` 和 `quantity`。
3. 验证返回订单状态为 `UNPAID`。
4. 验证 `order_item` 中保存了购买时商品快照。
5. 验证 `inventory.available_stock` 减少，`inventory.locked_stock` 增加。
6. 验证当前用户购物车没有新增、删除或数量变化。
7. 使用 mock 支付该订单，验证现有支付链路可以继续将锁定库存确认售出。
8. 创建另一个立即购买订单后取消，验证现有取消链路可以释放锁定库存。

## 文档更新计划

`BUY_NOW` 实现完成后，需要更新：

- `docs/backend-feature-mapping.md`：把 `BUY_NOW` 从未实现移动到已实现，并补充 API、模块、测试位置。
- `docs/api-testing-with-reqable.md`：补充立即购买手动测试步骤。
- `src/docs/asciidoc/api.adoc`：如果 REST Docs 已覆盖订单接口，则补充新接口片段。

## 审批门槛

本文档只定义设计和开发边界，不直接开始代码实现。

开始实现 `BUY_NOW` 前，需要确认：

- 第一版只支持单 SKU。
- 请求体采用 `BuyNowRequest(Long skuId, Integer quantity)`。
- 立即购买不处理购物车。
- 不在第一版新增订单来源字段。
- 不在第一版加入地址、优惠券、运费和发票。

确认后，下一步可以编写单独实现计划，或者直接按本文档拆分任务进入代码实现。
