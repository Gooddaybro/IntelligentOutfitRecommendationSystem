# 前后端与支付联调测试文档

本文档用于通过 IDEA 启动前后端，并使用 Reqable 手动测试当前系统的前端页面、Java 后端、Python AI 服务、订单和支付链路。

## 1. 测试目标

本轮测试需要确认：

- 前端页面可以正常启动并访问 Java 后端接口。
- Java 后端仍然是用户、商品、SKU、价格、库存、订单和支付状态的唯一可信来源。
- AI 推荐页面可以调用 Java 后端的 AI 聊天接口，并展示推荐商品卡片。
- 推荐卡片点击后必须经过用户确认，不能由 AI 自动下单或自动支付。
- 传统浏览页面可以独立完成搜索、加入购物车、立即购买、下单和支付。
- Mock 支付可以通过统一支付接口完成，并且支付回调接口可以记录回调请求。

## 2. 启动 MySQL

当前后端默认连接：

```text
host = localhost
port = 3307
database = intelligent_outfit
username = root
password = 123456
```

检查端口可以用 PowerShell，也可以直接看 IDEA 或数据库工具的连接结果：

```powershell
Test-NetConnection -ComputerName localhost -Port 3307
```

如果 MySQL 没有启动，可以在 Docker Desktop 中启动项目 MySQL，或在项目根目录执行：

```powershell
docker compose up -d mysql
```

## 3. 用 IDEA 启动 Java 后端

推荐用 IDEA 启动后端，不需要在命令行运行 Maven。

操作步骤：

1. 用 IDEA 打开当前项目根目录。
2. 确认 `backend/pom.xml` 已被 IDEA 识别为 Maven 项目。
3. 确认 Project SDK 使用 JDK 21。
4. 打开后端启动类 `IntelligentOutfitRecommendationSystemApplication`。
5. 点击 IDEA 右上角运行按钮启动 Spring Boot。

预期结果：

- 后端监听 `http://localhost:8080`。
- Flyway 自动执行数据库迁移。
- 控制台没有 MySQL 连接失败、端口占用或迁移失败错误。

基础检查建议直接用 Reqable 发请求：

```http
GET http://localhost:8080/api/products
```

预期结果：返回 `success=true`，并且 `data` 中有商品数据。

## 4. 启动 Python AI 服务

如果只测试商品、购物车、订单和支付，可以暂时不启动 Python。

如果要测试 AI 推荐页面、AI 流式回答和推荐卡片，需要启动 Python 服务，并确认 Java 后端配置指向 Python 服务地址：

```properties
app.ai.python-base-url=http://localhost:8000
```

Python 侧预期提供：

- `POST /chat`
- `POST /chat/stream`

如果 Python 没启动，AI 聊天会失败，但传统商品浏览、购物车、立即购买、订单和支付仍应通过 Java 后端正常工作。

## 5. 用 IDEA 启动前端页面

推荐在 IDEA 的前端运行配置里启动 Vite。

首次使用前，需要确认 `frontend` 目录已经安装依赖。可以在 IDEA Terminal 中进入 `frontend` 后执行一次：

```powershell
npm install
```

IDEA 启动方式：

1. 打开 `frontend/package.json`。
2. 在 `scripts.dev` 左侧点击运行，或新建 npm Run Configuration。
3. 配置 `package.json` 路径为 `frontend/package.json`。
4. Command 选择 `run`，Scripts 选择 `dev`。
5. 点击运行。

预期结果：

- Vite 输出本地访问地址，通常是 `http://localhost:5173`。
- 前端通过 `/api` 代理访问 `http://localhost:8080`。
- 浏览器打开 `http://localhost:5173` 后可以看到 AI 购物工作台。

## 6. 页面联调主流程

按下面顺序测试，可以覆盖当前核心业务闭环。

1. 注册或登录测试用户。
2. 打开传统浏览页面，确认商品列表可以加载。
3. 点击商品卡片的“加入购物车”，确认购物车数量增加。
4. 使用购物车结算或立即购买创建订单。
5. 打开订单页面，确认订单状态为 `UNPAID`。
6. 点击支付按钮，前端调用统一支付接口。
7. 支付成功后刷新订单列表，确认订单状态变为已支付状态。

联调时需要重点确认：

- 前端不传价格、库存、订单状态、支付状态或用户 ID。
- 商品价格、库存、订单金额和支付金额都来自 Java 后端响应。
- AI 推荐卡片点击后必须先弹出确认，不允许 AI 自动下单或自动支付。
- 传统浏览页面和 AI 推荐页面都只能通过 Java 后端完成购物车、订单和支付操作。

## 7. AI 推荐页面测试

打开 AI 推荐页面后，输入示例：

```text
我想买一套通勤穿搭，预算 500，身高 177cm，体重 65kg，想要简洁一点
```

预期结果：

- 页面开始显示 AI 回复。
- 浏览器 Network 中可以看到 `/api/assistant/chat/stream` 请求。
- 推荐商品卡片来自 Java 后端候选商品数据。
- 点击推荐卡片的“加入购物车”或“购买”时，页面先弹出确认框。

如果页面显示 AI 生成失败：

- 先看 Java 后端日志是否是调用 Python 失败。
- 再看 Python 控制台是否报错。
- 如果错误类似 `object is not subscriptable`，说明 Python 返回结构或流式完成事件结构不符合 Java/前端约定，需要优先修复 Python 的返回格式。

## 8. 传统浏览页面测试

打开传统浏览页面后测试：

- 搜索关键字是否能过滤商品。
- 商品卡片价格、颜色、尺码、库存是否正常展示。
- 加入购物车是否调用 `POST /api/cart/items`。
- 立即购买是否调用 `POST /api/orders/buy-now`。
- 右侧 AI 辅助聊天不能绕过 Java 后端直接生成价格、库存或订单。

## 9. 支付功能专项测试

本节以 Reqable 为主，不使用 PowerShell 直接请求。Reqable 中建议配置以下环境变量：

```text
base_url = http://localhost:8080
access_token = 登录后复制 data.accessToken
order_no = 创建订单后复制 data.orderNo
payment_no = 发起支付后复制 data.paymentNo
```

所有需要登录的接口都添加请求头：

```http
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

先注册或登录测试用户，并创建一个未支付订单。

登录示例：

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

预期结果：返回 `data.accessToken`，复制到 Reqable 环境变量 `access_token`。

创建立即购买订单：

```http
POST {{base_url}}/api/orders/buy-now
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "skuId": 2103,
  "quantity": 1
}
```

预期结果：

- `data.orderNo` 非空。
- `data.status=UNPAID`。
- `data.totalAmount` 由后端按 SKU 价格和数量计算。
- 复制 `data.orderNo` 到 Reqable 环境变量 `order_no`。

通过统一支付接口发起 Mock 支付：

```http
POST {{base_url}}/api/payments
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "orderNo": "{{order_no}}",
  "channel": "MOCK"
}
```

预期结果：

- `paymentNo` 非空。
- `orderNo` 等于刚创建的订单号。
- `amount` 等于后端订单金额。
- `channel=MOCK`。
- `status=SUCCESS`。
- `transactionId` 非空。
- 复制 `data.paymentNo` 到 Reqable 环境变量 `payment_no`。

查询支付单：

```http
GET {{base_url}}/api/payments/{{payment_no}}
Authorization: Bearer {{access_token}}
```

预期结果：返回同一笔支付单，普通用户只能查询自己的订单支付记录。

测试重复支付：

```http
POST {{base_url}}/api/payments
Authorization: Bearer {{access_token}}
Content-Type: application/json
```

```json
{
  "orderNo": "{{order_no}}",
  "channel": "MOCK"
}
```

预期结果：返回已存在的成功支付记录，不重复确认库存，也不重复生成新的成功支付事实。

测试回调接收接口：

```http
POST {{base_url}}/api/payments/callback/MOCK
Content-Type: application/json
```

```json
{
  "paymentNo": "{{payment_no}}",
  "status": "SUCCESS",
  "transactionId": "mock-callback-001"
}
```

预期结果：

- 返回 `success=true`。
- 当前第一阶段只记录回调日志，不依赖回调再次改变订单状态。
- 真实支付宝或微信支付接入后，才会在验签成功后通过回调推进支付状态。

## 10. 自动化回归测试

自动化回归不是 Reqable 测试，主要用于改完代码后确认后端和前端没有被破坏。可以在 IDEA Terminal 中执行。

后端回归：

```powershell
cd backend
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
```

前端回归：

```powershell
cd frontend
npm test -- --run
npm run build
```

预期结果：

- 后端 `mvnw.cmd verify` 通过。
- 前端 Vitest 全部通过。
- 前端生产构建成功。

## 11. 常见问题定位

前端页面打不开：

- 确认 `npm run dev` 是否还在运行。
- 确认访问的是 Vite 输出地址，例如 `http://localhost:5173`。

前端接口 404 或 500：

- 确认 Java 后端是否监听 `http://localhost:8080`。
- 查看浏览器 Network 中的真实请求路径。
- 查看 Spring Boot 控制台异常。

登录后仍然 401：

- 清理浏览器 localStorage 后重新登录。
- 确认请求头里有 `Authorization: Bearer <accessToken>`。

AI 推荐失败：

- 确认 Python AI 服务是否监听 `http://localhost:8000`。
- 确认 Python 的 `/chat/stream` 返回 SSE 事件格式。
- 先用传统浏览和订单支付流程确认 Java 后端本身正常。

支付失败：

- 确认订单属于当前登录用户。
- 确认订单状态仍为 `UNPAID`。
- 确认请求体只传 `orderNo` 和 `channel`，不要传金额、状态或用户 ID。
