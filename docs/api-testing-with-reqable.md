# Reqable 接口测试说明

本文用于手动验证当前 Java 后端接口。先启动 Spring Boot 应用，再按下面顺序测试。

Redis 缓存与 AI 限流的专项验证见 `docs/redis-cache-rate-limit-manual-test.md`。

## 0. 推荐测试顺序

如果你只是想快速判断“后端主链路是否可用”，按这个顺序测：

```text
1. 注册
2. 登录，复制 accessToken
3. /api/users/me 校验鉴权
4. /api/products 校验商品库
5. /api/assistant/chat 或 /api/assistant/chat/stream 校验 AI 入口
6. /api/cart/items 加购物车
7. /api/orders 或 /api/orders/buy-now 创建订单
8. /api/payments/mock-pay 模拟支付
```

Reqable 里建议建一个环境：

```text
base_url = http://127.0.0.1:8080
access_token = 登录后复制 data.accessToken
refresh_token = 登录后复制 data.refreshToken
thread_id = assistant/chat 返回的 data.threadId
order_no = 创建订单后复制 data.orderNo
```

所有需要登录的请求都加：

```http
Authorization: Bearer {{access_token}}
```

判断边界是否正确时，重点看：

- 前端或 Reqable 请求体不能传 `userId`、订单金额、支付状态。
- 商品价格、库存、订单金额必须来自 Java 后端响应。
- Python AI 不启动时，商品、购物车、订单、支付接口仍应可用。

## 1. 环境变量

```text
base_url = http://127.0.0.1:8080
internal_token = dev-internal-token
access_token = 登录后复制 data.accessToken
refresh_token = 登录后复制 data.refreshToken
thread_id = 创建会话或 assistant/chat 返回的 data.threadId
```

## 2. 注册用户

```http
POST {{base_url}}/api/auth/register
Content-Type: application/json
```

```json
{
  "username": "tester001",
  "password": "StrongPassword123!",
  "email": "tester001@example.com"
}
```

期望：`200 OK`，返回 `data.userId`、`data.username`、`data.status=active`。

## 3. 登录并获取 Token

```http
POST {{base_url}}/api/auth/login
Content-Type: application/json
```

```json
{
  "username": "tester001",
  "password": "StrongPassword123!"
}
```

期望：`200 OK`，复制：

```text
data.accessToken  -> access_token
data.refreshToken -> refresh_token
```

## 4. 测试当前用户

不带 token：

```http
GET {{base_url}}/api/users/me
```

期望：`401 Unauthorized`。

带 token：

```http
GET {{base_url}}/api/users/me
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，返回 `ROLE_USER`。

## 5. 测试用户画像

```http
PUT {{base_url}}/api/me/profile
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "nickname": "Alex",
  "avatarUrl": "https://example.com/avatar.png",
  "gender": "male",
  "birthday": "1998-05-20"
}
```

读取：

```http
GET {{base_url}}/api/me/profile
Authorization: Bearer {{access_token}}
```

## 6. 测试身体数据

```http
PUT {{base_url}}/api/me/body-data
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "heightCm": 178.5,
  "weightKg": 70.2,
  "shoulderWidthCm": 45.0,
  "bustCm": 96.0,
  "waistCm": 80.0,
  "hipCm": 95.0,
  "preferredFit": "regular"
}
```

读取：

```http
GET {{base_url}}/api/me/body-data
Authorization: Bearer {{access_token}}
```

## 7. 测试穿衣偏好

```http
PUT {{base_url}}/api/me/preferences
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "preferredStyles": ["commute", "minimal"],
  "preferredColors": ["black", "navy"],
  "dislikedColors": ["orange"],
  "preferredCategories": ["jacket", "pants"],
  "budgetMin": 100,
  "budgetMax": 500
}
```

读取：

```http
GET {{base_url}}/api/me/preferences
Authorization: Bearer {{access_token}}
```

## 8. 测试会话记录

创建会话：

```http
POST {{base_url}}/api/conversations
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "title": "秋季通勤外套"
}
```

期望：`200 OK`，复制 `data.threadId -> thread_id`。

查询会话列表：

```http
GET {{base_url}}/api/conversations
Authorization: Bearer {{access_token}}
```

查询消息历史：

```http
GET {{base_url}}/api/conversations/{{thread_id}}/messages
Authorization: Bearer {{access_token}}
```

归档会话：

```http
DELETE {{base_url}}/api/conversations/{{thread_id}}
Authorization: Bearer {{access_token}}
```

期望：`200 OK`。归档后再次查询会话列表，列表中不再出现该 `threadId`。

## 8.1 测试商品和图片

```http
GET {{base_url}}/api/products
```

期望：`200 OK`，`data[*].mainImageUrl` 应返回本地静态图片路径，例如：

```text
/images/products/tshirt-basic-main.svg
/images/products/jacket-commute-main.svg
/images/products/pants-straight-main.svg
```

再直接请求其中一张图片：

```http
GET {{base_url}}/images/products/jacket-commute-main.svg
```

期望：`200 OK`，响应内容是 SVG。若返回 404，优先检查数据库 `product_spu.main_image_url`、`product_image.image_url` 是否已执行 Flyway `V9__use_local_svg_product_images.sql`，以及后端 `src/main/resources/static/images/products/` 下是否有对应文件。

前端本地开发时，Vite 也会从 `frontend/public/images/products/` 提供同名图片。因此网页里看到的 `/images/products/*.svg` 可以由前端 dev server 或 Java 后端正确加载。

## 9. 测试 AI 同步问答

注意：这个接口会真实调用 `application.properties` 里的 Python 地址：

```properties
app.ai.python-base-url=http://localhost:8000
```

如果 Python 服务没有启动，期望返回 `502 Bad Gateway`、`errorCode=external_service_error`，并使用固定安全文案，不返回 Python 连接细节、堆栈或 provider 原始错误。如果 Python 已启动并提供 `POST /chat`，请求如下：

```http
POST {{base_url}}/api/assistant/chat
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "threadId": null,
  "message": "我想买一件适合秋季通勤的外套，预算 800 以内",
  "category": "外套",
  "style": "commute",
  "season": "autumn",
  "material": null,
  "fit": "regular",
  "budgetMax": 800
}
```

期望：`200 OK`，返回：

- `data.threadId`
- `data.answer`
- `data.recommendedSpuIds`
- `data.recommendedItems`
- `data.candidatesCount`

`data.recommendedItems[*]` 应包含：

```json
{
  "spuId": 1002,
  "skuId": 2004,
  "reason": "符合秋季通勤、regular 版型和预算条件",
  "rankScore": 0.95
}
```

然后用返回的 `threadId` 查询消息历史：

```http
GET {{base_url}}/api/conversations/{{thread_id}}/messages
Authorization: Bearer {{access_token}}
```

期望：至少包含两条消息：`role=user` 和 `role=assistant`。

Python `/chat` 第一版建议返回：

```json
{
  "request_id": "req-xxx",
  "answer": "推荐优先看通勤外套，版型选择 regular，更适合秋季叠穿。",
  "intent": "recommendation",
  "product_refs": [
    {
      "spu_id": 1002,
      "sku_id": 2004,
      "reason": "符合秋季通勤、regular 版型和预算条件",
      "rank_score": 0.95
    }
  ],
  "suggested_actions": []
}
```

Java 会先用本轮 Java 候选池过滤 Python 返回的 `product_refs`。过滤通过后：

- `product_refs[*].spu_id` 会转成 `/api/assistant/chat` 响应里的 `data.recommendedSpuIds`。
- `product_refs[*].spu_id`、`sku_id`、`reason`、`rank_score` 会转成 `data.recommendedItems[]`。
- 候选池外的 `spu_id`/`sku_id` 不会透出给前端。

## 10. 测试 AI 流式问答

注意：这个接口会真实调用 Python：

```properties
app.ai.python-base-url=http://localhost:8000
app.ai.stream-timeout-ms=120000
```

如果 Python 服务没有启动，Java 会返回 SSE `error` 事件。Python 已启动并提供 `POST /chat/stream` 时，请求如下：

```http
POST {{base_url}}/api/assistant/chat/stream
Authorization: Bearer {{access_token}}
Content-Type: application/json
Accept: text/event-stream
```

```json
{
  "threadId": null,
  "message": "我身高175体重70kg，适合穿什么码？",
  "category": "外套",
  "style": "commute",
  "season": "autumn",
  "material": null,
  "fit": "regular",
  "budgetMax": 800
}
```

期望响应头：

```http
Content-Type: text/event-stream
```

期望事件顺序：

```text
event:meta
data:{"request_id":"...","thread_id":"th_..."}

event:token
data:{"content":"我建议"}

event:done
data:{"thread_id":"th_...","answer":"我建议您穿 L 码。","recommended_spu_ids":[],"recommended_items":[],"candidates_count":12,"intent":"size_recommendation"}
```

如果 Python 生成失败，期望：

```text
event:error
data:{"code":"internal_error","message":"大模型生成异常"}
```

Java 行为边界：

- 前端仍然只传 `AssistantChatRequest`，不能传 `user_context`、`candidates`、价格、库存或订单状态。
- Java 会先保存 user 消息，再调用 Python `/chat/stream`。
- Java 只在 Python `done` 后保存完整 assistant 消息。
- 前端主动取消请求属于正常业务行为，不应视为服务端错误。

Reqable 里查看 SSE 时，重点检查：

- 响应头是 `text/event-stream`。
- 先出现 `meta` 或类似线程信息事件。
- 中间可以有多个 `token`。
- 最后必须有 `done` 或 `error`。
- `data:` 后面的内容应是单行 JSON，不应出现多行 JSON。
- `done` 里的推荐商品 id 只能来自 Java 候选商品，不应凭空出现。
- `done` 里的 `recommended_items` 可以包含推荐理由和排序分，前端推荐卡片会展示 `reason`。

## 11. 测试购物车

购物车接口只使用普通用户 `Authorization: Bearer <access_token>`，不需要也不支持 `X-Internal-Token`。本阶段购物车只保存购买意图，不锁库存、不创建订单、不调用 Python。

查询空购物车：

```http
GET {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，`data` 是数组。

添加 SKU 到购物车：

```http
POST {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2102,
  "quantity": 1
}
```

期望：`200 OK`，返回列表中包含 `skuId=2102`、`spuCode=JACKET_COMMUTE_001`、`quantity=1`。

重复添加同一 SKU：

```http
POST {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2102,
  "quantity": 2
}
```

期望：`quantity` 累加为 `3`。

修改数量：

```http
PUT {{base_url}}/api/cart/items/2102
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "quantity": 5
}
```

期望：`200 OK`，`quantity=5`。

删除单个 SKU：

```http
DELETE {{base_url}}/api/cart/items/2102
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，返回当前购物车列表。

清空购物车：

```http
DELETE {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
```

期望：`200 OK`。再次查询 `/api/cart/items` 时 `data` 为空数组。

参数校验：

```http
POST {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2102,
  "quantity": 0
}
```

期望：`400 Bad Request`，`errorCode=validation_failed`。

把 `quantity` 改成 `100` 时也应返回 `400 Bad Request`，当前购物车数量上限为 `99`。

## 11.1 Reqable 常见排错

### 401 Unauthorized

通常是没有带：

```http
Authorization: Bearer {{access_token}}
```

或者 token 已过期。重新调用登录接口，复制新的 `data.accessToken`。

### 400 validation_failed

通常是请求体字段不符合后端校验，例如：

- `quantity=0`
- `quantity>99`
- 缺少必填字段
- 字段类型错误

### 502 external_service_error

通常是 Java 调 Python AI 服务失败。响应文案应是固定安全文案，不应包含 Python URL、堆栈、API key、prompt 或 provider 原始错误。先确认：

```text
http://localhost:8000/health
```

如果 Python 没启动，AI 聊天失败是预期行为；商品、购物车、订单、支付不应受影响。

### SSE 看不到流式 token

检查请求头：

```http
Accept: text/event-stream
```

再检查 Python 是否提供 `/chat/stream`，以及 Java 配置的 `app.ai.python-base-url` 是否正确。

## 12. 测试订单购物车结算和立即购买

订单接口只使用普通用户 `Authorization: Bearer <access_token>`。本阶段支持购物车结算和单 SKU 立即购买，Python AI 不参与下单，前端不能传金额、订单状态或用户 ID。

先确保购物车里有可结算 SKU：

```http
POST {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2103,
  "quantity": 1
}
```

再添加第二个 SKU：

```http
POST {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2203,
  "quantity": 2
}
```

从购物车创建订单：

```http
POST {{base_url}}/api/orders
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "source": "CART",
  "skuIds": [2103, 2203]
}
```

期望：`200 OK`，返回：

- `data.orderNo` 非空，形如 `ORD20260602113000123456`。
- `data.status=UNPAID`。
- `data.totalAmount` 由后端按数据库价格和购物车数量计算。
- `data.items[*].salePrice`、`quantity`、`lineAmount` 来自后端快照，不来自前端请求。

创建成功后，再查购物车：

```http
GET {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
```

期望：刚刚结算的 `2103` 和 `2203` 已从当前用户购物车移除。

查询当前用户订单列表：

```http
GET {{base_url}}/api/orders
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，`data` 中包含刚创建的 `orderNo`。

查询订单详情：

```http
GET {{base_url}}/api/orders/{{order_no}}
Authorization: Bearer {{access_token}}
```

期望：`200 OK`，返回订单主信息和 `items` 快照。

立即购买单个 SKU：

```http
POST {{base_url}}/api/orders/buy-now
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2103,
  "quantity": 2
}
```

期望：`200 OK`，返回：

- `data.orderNo` 非空。
- `data.status=UNPAID`。
- `data.totalAmount` 由后端按数据库 SKU 价格和 `quantity` 计算。
- `data.items[0].skuId=2103`。
- `data.items[0].quantity=2`。
- `data.items[0].lineAmount` 等于后端价格乘以数量。

立即购买成功后，再查购物车：

```http
GET {{base_url}}/api/cart/items
Authorization: Bearer {{access_token}}
```

期望：立即购买不会新增、删除或修改当前用户购物车条目。

立即购买参数校验：

```http
POST {{base_url}}/api/orders/buy-now
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2103,
  "quantity": 0
}
```

期望：`400 Bad Request`，`errorCode=validation_failed`。

## 13. 测试 Mock 支付和订单取消

支付接口只使用普通用户 `Authorization: Bearer <access_token>`。Python AI 不参与支付，前端不能传金额、渠道、用户 ID 或支付状态。

先用第 11 节流程创建一个 `UNPAID` 订单，并把返回的 `data.orderNo` 保存为 `{{order_no}}`。

模拟支付：

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

期望：`200 OK`，返回：

- `data.paymentNo` 非空，形如 `PAY20260602114500123456`。
- `data.orderNo={{order_no}}`。
- `data.amount` 等于订单后端计算的总金额。
- `data.channel=MOCK`。
- `data.status=SUCCESS`。
- `data.transactionId` 非空。
- `data.paidAt` 非空。

重复提交同一个支付请求：

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

期望：仍为 `200 OK`，返回同一个 `paymentNo`，不会重复把库存从 `locked_stock` 转为 `sold_stock`。

取消未支付订单：

先重新创建一个新的 `UNPAID` 订单，把订单号保存为 `{{cancel_order_no}}`。

```http
POST {{base_url}}/api/orders/{{cancel_order_no}}/cancel
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "reason": "用户不想买了"
}
```

期望：`200 OK`，返回：

- `data.orderNo={{cancel_order_no}}`。
- `data.status=CANCELLED`。
- `data.closedAt` 非空。
- `data.closeReason=用户不想买了`。

取消已支付订单：

如果对已经 `PAID` 的订单调用取消接口，期望：`400 Bad Request`，`errorCode=bad_request`。当前 MVP 不做退款、发货、确认收货或售后。

跨用户支付或取消：

用另一个用户的 access token 调用 `POST /api/payments/mock-pay` 或 `POST /api/orders/{{order_no}}/cancel`，期望：`404 Not Found`，`errorCode=not_found`。

超时关闭：

MVP 使用 Spring `@Scheduled` 轮询超时未支付订单，默认配置：

```properties
order.unpaid-timeout-minutes=30
order.timeout-close-batch-size=50
order.timeout-close-fixed-delay-ms=60000
```

超时关闭后的订单状态为 `CLOSED`，锁定库存会释放回 `available_stock`。

## 14. 刷新与登出

刷新：

```http
POST {{base_url}}/api/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

期望：返回新的 `accessToken` 和 `refreshToken`。旧的 refresh token 会被撤销。

登出：

```http
POST {{base_url}}/api/auth/logout
Content-Type: application/json
```

```json
{
  "refreshToken": "{{refresh_token}}"
}
```

期望：`200 OK`。再次用同一个 refresh token 调 `/api/auth/refresh` 应返回 `400 Bad Request`。

## 15. Internal API

普通 Bearer Token 不能替代内部 token。Python AI 服务调用 internal API 时仍然使用：

```http
GET {{base_url}}/internal/inventory?skuId=2001
X-Internal-Token: {{internal_token}}
```
