# Frontend Backend Integration Test Guide

本文档用于本地前后端联调，覆盖启动检查、鉴权、商品、AI 导购、购物车、订单和模拟支付流程。

自动化 E2E 质量门禁的补齐方案见 `docs/superpowers/specs/2026-06-19-frontend-e2e-quality-gate-design.md`。

## 1. 环境准备

1. 确认 Java 后端使用 JDK 21。

   ```powershell
   java -version
   $env:JAVA_HOME
   ```

   如果 `JAVA_HOME` 仍指向 JDK 11，本项目 Maven 测试会失败。当前会话可临时修正：

   ```powershell
   $env:JAVA_HOME='D:\Program Files\Java\jdk-21'
   $env:Path="$env:JAVA_HOME\bin;$env:Path"
   ```

   如需永久修正，在 Windows 系统环境变量中把 `JAVA_HOME` 改为 `D:\Program Files\Java\jdk-21`。

2. 确认 MySQL 监听 `3307`，数据库名为 `intelligent_outfit`，root 密码为 `123456`。

   ```powershell
   Test-NetConnection -ComputerName localhost -Port 3307
   ```

   如果未监听，启动项目自带 MySQL：

   ```powershell
   docker compose up -d mysql
   ```

3. 确认 Node.js 和前端依赖。

   ```powershell
   cd frontend
   npm install
   ```

## 2. 启动服务

1. 启动 Java 后端。

   ```powershell
   cd backend
   $env:JAVA_HOME='D:\Program Files\Java\jdk-21'
   $env:Path="$env:JAVA_HOME\bin;$env:Path"
   .\mvnw.cmd spring-boot:run
   ```

   预期结果：后端监听 `http://localhost:8080`。

2. 启动前端。

   ```powershell
   cd frontend
   npm run dev
   ```

   预期结果：Vite 输出本地访问地址，默认通过 `/api` 代理到 `http://localhost:8080`。

3. 基础连通性检查。

   ```powershell
   Invoke-RestMethod http://localhost:8080/api/products
   ```

   预期结果：返回 `success=true`，`data` 中包含种子商品。

## 3. 后端接口冒烟测试

以下命令在 PowerShell 中执行。

1. 注册并登录测试用户。

   ```powershell
   $base = "http://localhost:8080"
   $username = "test_" + (Get-Date -Format "HHmmss")
   $password = "Test123456"

   Invoke-RestMethod "$base/api/auth/register" `
     -Method Post `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ username=$username; password=$password; email="$username@example.com" } | ConvertTo-Json)

   $login = Invoke-RestMethod "$base/api/auth/login" `
     -Method Post `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ username=$username; password=$password } | ConvertTo-Json)

   $headers = @{ Authorization = "Bearer $($login.data.accessToken)" }
   ```

   预期结果：登录返回 `accessToken` 和 `refreshToken`。

2. 校验鉴权。

   ```powershell
   Invoke-RestMethod "$base/api/users/me" -Headers $headers
   ```

   预期结果：返回当前用户信息。未带 token 访问应返回 401。

3. 校验商品浏览和推荐候选。

   ```powershell
   Invoke-RestMethod "$base/api/products"
   Invoke-RestMethod "$base/api/products/recommendation-candidates?style=commute&limit=3"
   Invoke-RestMethod "$base/api/products/1001"
   ```

   预期结果：商品列表、候选 SKU、商品详情均返回后端商品库数据。商品图片字段应是本地静态路径，例如 `/images/products/tshirt-basic-main.svg` 或 `/images/products/jacket-commute-main.svg`。

   如需单独验证图片：

   ```powershell
   Invoke-WebRequest "$base/images/products/jacket-commute-main.svg"
   ```

   预期结果：HTTP 200，浏览器 Network 中同一路径也应返回 200。如果这里 404，先检查数据库图片 URL 是否仍是旧的 `.jpg`，以及 Flyway `V9__use_local_svg_product_images.sql` 是否已执行。

4. 校验购物车。

   ```powershell
   Invoke-RestMethod "$base/api/cart/items" `
     -Method Post `
     -Headers $headers `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ skuId=2001; quantity=1 } | ConvertTo-Json)

   Invoke-RestMethod "$base/api/cart/items" -Headers $headers
   ```

   预期结果：购物车包含 SKU `2001`，数量为 `1`。

5. 校验立即购买和模拟支付。

   ```powershell
   $order = Invoke-RestMethod "$base/api/orders/buy-now" `
     -Method Post `
     -Headers $headers `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ skuId=2001; quantity=1 } | ConvertTo-Json)

   $order.data.status

   Invoke-RestMethod "$base/api/payments/mock-pay" `
     -Method Post `
     -Headers $headers `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ orderNo=$order.data.orderNo } | ConvertTo-Json)
   ```

   预期结果：订单创建后状态为 `UNPAID`，模拟支付返回 `SUCCESS`。

6. 校验 AI 导购普通接口。

   ```powershell
   Invoke-RestMethod "$base/api/assistant/chat" `
     -Method Post `
     -Headers $headers `
     -ContentType "application/json; charset=utf-8" `
     -Body (@{ message="通勤穿搭，预算 300"; style="commute"; budgetMax=300 } | ConvertTo-Json)
   ```

   预期结果：Java 后端先筛选候选商品，再调用 Python AI 服务。若 Python 服务未启动，应返回可诊断的外部服务错误。

## 4. 前端页面联调

1. 打开前端页面。

   访问 Vite 输出的地址，例如 `http://localhost:5173`。

2. 注册或登录。

   输入测试用户名和密码，提交后应进入主界面。刷新页面后，前端应通过本地 token 调用 `/api/users/me` 恢复登录态。

3. AI 推荐页。

   输入穿搭需求，例如“通勤、预算 300、想要简洁一点”。检查浏览器 Network：

   - `/api/assistant/chat/stream` 返回 SSE 流，或在异常时前端回退调用 `/api/assistant/chat`。
   - 推荐卡片展示的 `skuId`、价格和库存来自 Java 后端候选商品。
   - 推荐卡片展示 `recommendedItems[*].reason` 对应的推荐理由，例如风格匹配、预算匹配、颜色匹配或库存可用。
   - 推荐卡片图片应加载 `/images/products/*.svg`，Network 状态应为 200，不应为 404。
   - 点击加入购物车或立即购买时，前端必须弹出确认，不应自动下单。

4. 传统浏览页。

   搜索关键词或直接浏览商品。检查：

   - 商品列表来自 `/api/products`。
   - 商品详情来自 `/api/products/{spuId}`。
   - 右侧 AI 面板不绕过 Java 商品、价格、库存接口。

5. 购物车页。

   添加 SKU 后进入购物车页。检查：

   - 数量修改调用 `PUT /api/cart/items/{skuId}`。
   - 删除商品调用 `DELETE /api/cart/items/{skuId}`。
   - 购物车结算调用 `POST /api/orders`，请求体只提交 `source` 和 `skuIds`，不提交价格、金额或 userId。

6. 订单页和支付。

   创建订单后进入订单页。检查：

   - 订单列表来自 `GET /api/orders`。
   - 单个订单详情来自 `GET /api/orders/{orderNo}`。
   - 模拟支付可调用 `POST /api/payments/mock-pay`，只提交 `orderNo`；当前前端订单页使用 `POST /api/payments` 并提交 `orderNo` 和 `channel=MOCK`。
   - 支付后订单状态变更为已支付状态，金额由后端返回，不由前端计算。

## 4.1 网页联调最小路径

如果只想确认前后端主流程是否通，可以按这个网页路径操作：

```text
1. 打开 http://localhost:5173
2. 注册一个新用户，或用已有用户登录
3. 进入 AI 推荐页
4. 输入：通勤、预算 300、想要简洁一点
5. 看聊天区域是否出现回答
6. 看推荐卡片是否显示商品名、价格、库存、推荐理由和商品图片
7. 点击加入购物车
8. 确认弹窗出现后先点取消，确认购物车数量不变
9. 再点击加入购物车并确认
10. 进入购物车页，确认 SKU 和数量正确
11. 点击结算，进入订单页
12. 点击 mock 支付，确认订单状态更新
```

浏览器 DevTools 的 Network 面板应看到：

```text
POST /api/auth/register 或 /api/auth/login
GET  /api/users/me
POST /api/assistant/chat/stream
GET  /api/products 或 /api/products/recommendation-candidates
GET  /images/products/*.svg
POST /api/cart/items
GET  /api/cart/items
POST /api/orders 或 /api/orders/buy-now
POST /api/payments 或 /api/payments/mock-pay
```

关键检查：

- `POST /api/cart/items` 请求体只应有 `skuId` 和 `quantity`。
- `POST /api/orders` 请求体只应有 `source` 和 `skuIds`。
- `POST /api/orders/buy-now` 请求体只应有 `skuId` 和 `quantity`。
- `POST /api/payments/mock-pay` 请求体只应有 `orderNo`。
- 当前前端订单页的 `POST /api/payments` 请求体只应有 `orderNo` 和 `channel=MOCK`。
- 前端不应提交价格、金额、userId、订单状态或支付状态。
- 推荐理由应来自 assistant 响应里的 `recommendedItems`，不是前端自己拼出来。
- 商品图片请求应返回 200；如果图片地址是 `/images/products/*.jpg`，说明数据库迁移或测试数据还没更新到本地 SVG 路径。

## 4.2 网页联调失败时怎么定位

1. 登录后又回到登录页。

   检查 Network 里的 `GET /api/users/me` 是否 401。如果是，重新登录并确认 localStorage 里有 token。

2. AI 推荐没有回答。

   检查 `POST /api/assistant/chat/stream`：

   - 如果是 502，优先启动 Python AI 服务。
   - 如果有 SSE `error`，看 Java 后端日志和 Python 日志。
   - 如果 Python 没启动，商品浏览、购物车、订单仍应继续可用。

3. 推荐卡片没有出现。

   检查 `/api/products/recommendation-candidates` 是否返回候选商品；再检查 assistant `done` 事件里是否有推荐 `spuIds`。

4. 推荐理由没有出现。

   检查 assistant `done` 事件或同步响应里是否有 `recommended_items`/`recommendedItems`。如果只有 `recommended_spu_ids`/`recommendedSpuIds`，商品仍可展示，但不会显示推荐理由。

5. 商品图片看不到。

   先在 Network 里点开图片请求：

   - 404：检查数据库 `main_image_url` 是否是 `/images/products/*.svg`，并确认后端 `src/main/resources/static/images/products/`、前端 `public/images/products/` 有同名文件。
   - 200 但页面不显示：检查图片元素是否被 CSS 压到 0 尺寸，或浏览器是否加载了旧缓存。

6. 加购物车失败。

   检查请求是否带 `Authorization`，以及 `skuId` 是否来自后端商品数据。

7. 订单金额不对。

   不看前端计算，直接看 `POST /api/orders` 或 `POST /api/orders/buy-now` 的响应。金额应由 Java 后端返回。

## 5. 回归验证命令

后端：

```powershell
cd backend
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
```

前端：

```powershell
cd frontend
npm test -- --run
npm run test:e2e
npm run build
```

后续接入 Playwright 后，前端还应补充：

```powershell
cd frontend
npm run test:e2e
```

## 6. 常见失败定位

1. 根目录运行 `.\mvnw.cmd` 失败。

   当前仓库已改为 `backend/` 和 `frontend/` 目录结构，应进入 `backend` 后运行 Maven。

2. Maven 报 `UnsupportedClassVersionError`。

   `JAVA_HOME` 指向了 JDK 11。改为 JDK 21 后重新运行。

3. 后端启动时报 MySQL 连接失败。

   检查 `localhost:3307` 是否监听，确认 `application.properties` 中的用户名和密码与本地 MySQL 一致。

4. AI 聊天失败。

   检查 Python AI 服务是否监听 `http://localhost:8000`。商品、购物车、订单和支付仍应只通过 Java 后端完成。
