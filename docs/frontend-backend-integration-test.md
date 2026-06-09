# Frontend Backend Integration Test Guide

本文档用于本地前后端联调，覆盖启动检查、鉴权、商品、AI 导购、购物车、订单和模拟支付流程。

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

   预期结果：商品列表、候选 SKU、商品详情均返回后端商品库数据。

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
   - 模拟支付调用 `POST /api/payments/mock-pay`，只提交 `orderNo`。
   - 支付后订单状态变更为已支付状态，金额由后端返回，不由前端计算。

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
npm run build
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

