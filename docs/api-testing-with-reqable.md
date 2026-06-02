# Reqable 接口测试说明

本文用于手动验证当前 Java 后端接口。先启动 Spring Boot 应用，再按下面顺序测试。

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

## 9. 测试 AI 同步问答

注意：这个接口会真实调用 `application.properties` 里的 Python 地址：

```properties
app.ai.python-base-url=http://localhost:8000
```

如果 Python 服务没有启动，期望返回 `502 Bad Gateway` 和 `errorCode=external_service_error`。如果 Python 已启动并提供 `POST /chat`，请求如下：

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

期望：`200 OK`，返回 `data.threadId`、`data.answer`、`data.recommendedSpuIds`、`data.candidatesCount`。

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

Java 会把 `product_refs[*].spu_id` 转成 `/api/assistant/chat` 响应里的 `data.recommendedSpuIds`。

## 10. 测试购物车

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

## 11. 测试订单购物车结算

订单接口只使用普通用户 `Authorization: Bearer <access_token>`。本阶段只支持购物车结算，Python AI 不参与下单，`BUY_NOW` 立即购买暂未实现。

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

阶段性拒绝立即购买：

```http
POST {{base_url}}/api/orders
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "source": "BUY_NOW",
  "skuIds": [2103]
}
```

期望：`400 Bad Request`，`errorCode=bad_request`。`BUY_NOW` 会在下一阶段作为单独功能开发。

## 12. 刷新与登出

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

## 13. Internal API

普通 Bearer Token 不能替代内部 token。Python AI 服务调用 internal API 时仍然使用：

```http
GET {{base_url}}/internal/inventory?skuId=2001
X-Internal-Token: {{internal_token}}
```
