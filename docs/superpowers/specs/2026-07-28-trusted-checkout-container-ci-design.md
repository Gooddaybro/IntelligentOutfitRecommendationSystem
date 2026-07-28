# 可信结算、应用容器化与分层 CI 设计

**日期：** 2026-07-28

**状态：** 待项目所有者最终确认

**涉及仓库：**

- `IntelligentOutfitRecommendationSystem`：Java、React、Compose 和跨服务 CI 的主仓库。
- `python-LangChainRAG`：Python AI 服务及其镜像和独立 CI。

## 1. 背景

项目已经具备商品、购物车、订单、支付、Java/Python 推荐调用、下单幂等和部分契约测试，但仍存在三类关键缺口：

1. 前端已经实现地址簿与结算页面，Java 后端却没有真实的地址和结算接口。
2. 现有 `docker-compose.yml` 只启动基础设施，没有 Java、Python 和前端应用镜像。
3. Java、Python CI 相互独立，前端没有进入 Java 仓库的主要 CI，也没有不使用 HTTP Mock 的跨服务交易验证。

本设计按纵向链路推进：

```text
可信结算闭环
→ 应用级容器化
→ 跨服务黄金链路
→ 分层 CI
```

这样每个阶段均可独立验收，失败时也能区分业务代码、容器环境和 CI 编排问题。

## 2. 目标

### 2.1 业务目标

- 当前用户可以维护自己的收货地址。
- 结算预览完全根据服务端价格、购物车数量、商品状态、库存和地址归属计算。
- 创建订单时重新校验所有交易事实，不信任预览结果或前端金额。
- 订单保存地址快照，地址簿的后续变化不影响历史订单。
- 现有下单幂等能力覆盖地址选择，并继续保证并发请求只产生一个订单。

### 2.2 工程目标

- 在全新环境执行 `docker compose up --build` 后，可访问完整系统。
- Java、Python、前端均有可重复构建的应用镜像和健康检查。
- PR CI 快速反馈各技术栈结果。
- 主分支、定时或手动 CI 可以启动真实依赖并完成一条跨服务黄金链路。
- CI 失败时保留足够且脱敏的诊断证据。

## 3. 非目标

本轮不实现：

- 优惠券、营销活动或复杂运费引擎。
- 真实第三方支付。
- Kafka、Kubernetes、分布式事务框架。
- 结算结果缓存或结算令牌。
- Elasticsearch/Kibana 作为默认启动依赖。
- 大规模拆分现有订单模块。
- 依赖外部大模型逐字输出的阻断式 CI。

第一版明确使用：

```text
shippingAmount = 0
discountAmount = 0
payableAmount = merchandiseAmount
```

响应保留运费和优惠字段，为以后扩展提供稳定契约，但不虚构尚不存在的业务能力。

## 4. 总体架构

Java 继续作为用户、地址、商品、SKU、价格、库存、购物车、订单和支付的事实源。Python 只承担推荐编排和自然语言能力，不参与交易金额或地址归属判断。

```text
Browser
   ↓
Frontend / Nginx
   ↓ /api
Java
   ├─ Address
   ├─ Checkout
   ├─ Order / Idempotency
   ├─ Payment
   ├─ MySQL
   ├─ Redis
   ├─ RabbitMQ
   └─ Python
        └─ LangGraph PostgreSQL
```

新增 Java 业务边界：

```text
address/
├─ api
├─ dto
├─ mapper
├─ model
└─ service

checkout/
├─ api
├─ dto
└─ service
```

订单模块仍负责创建订单、库存锁定、商品快照、地址快照、幂等和状态流转。

## 5. 地址管理设计

### 5.1 数据表

新增 Flyway 迁移 `V26__address_checkout_schema.sql`。

`user_address`：

| 字段 | 说明 |
| --- | --- |
| `id` | 地址主键 |
| `user_id` | 地址所有者 |
| `recipient_name` | 收货人 |
| `phone` | 手机号 |
| `province` | 省级行政区 |
| `city` | 城市 |
| `district` | 区县 |
| `detail` | 详细地址 |
| `is_default` | 是否默认 |
| `created_at` | 创建时间 |
| `updated_at` | 更新时间 |

索引至少覆盖：

```text
(user_id, is_default)
(user_id, updated_at)
```

所有权校验不依赖前端传入的用户信息。更新和删除 SQL 必须同时使用：

```sql
WHERE id = :addressId AND user_id = :currentUserId
```

### 5.2 API

```http
GET    /api/addresses
POST   /api/addresses
PUT    /api/addresses/{addressId}
DELETE /api/addresses/{addressId}
PUT    /api/addresses/{addressId}/default
```

接口请求中不接受 `userId`。用户身份只来自服务端认证上下文。

### 5.3 默认地址规则

- 首个地址自动成为默认地址。
- 将一个地址设为默认时，在同一事务中清除该用户的其他默认标记。
- 删除默认地址后，将剩余地址中最近更新的一条设为默认。
- 删除最后一个地址后，用户没有默认地址。
- 并发修改默认地址时对当前用户或其地址集合建立数据库串行化边界，最终最多只有一个默认地址。

不使用复杂生成列模拟条件唯一索引，默认地址唯一性由事务逻辑和并发集成测试保证。

### 5.4 校验与错误语义

- 收货人、手机号、省、市、区县和详细地址不能为空。
- 对字段长度和手机号格式进行服务端校验。
- 不接入外部地址真实性验证。
- 地址不存在和地址不属于当前用户使用相同的公开错误语义，避免泄露其他用户资源是否存在。

## 6. 结算预览设计

### 6.1 API

```http
POST /api/checkout/preview
```

请求：

```json
{
  "skuIds": [11, 12],
  "addressId": 1
}
```

响应：

```json
{
  "items": [],
  "merchandiseAmount": 699.00,
  "shippingAmount": 0.00,
  "discountAmount": 0.00,
  "payableAmount": 699.00,
  "invalidReasons": []
}
```

### 6.2 统一计算边界

增加内部 `CheckoutCalculator`，输出不可变的 `CheckoutCalculation`：

```text
CheckoutCalculation
├─ items
├─ merchandiseAmount
├─ shippingAmount
├─ discountAmount
├─ payableAmount
└─ invalidReasons
```

提供两个业务入口：

```text
previewCart(userId, skuIds, addressId)
calculateForOrder(userId, skuIds, addressId)
```

二者共享数据读取和金额规则：

- 购物车数量来自服务端购物车。
- SKU、SPU 状态来自商品表。
- 价格来自当前 SKU 销售价。
- 库存来自当前库存事实。
- 地址必须属于当前用户。
- 金额使用 `BigDecimal`。

差异：

- 预览时，库存不足或商品下架进入 `invalidReasons`，供页面展示。
- 正式下单时，相同问题立即阻止创建订单。
- 认证失败、参数非法和地址越权不进入 `invalidReasons`，而是使用标准 API 错误。

### 6.3 金额计算

```text
lineAmount = salePrice × quantity
merchandiseAmount = Σ lineAmount
payableAmount = merchandiseAmount + shippingAmount - discountAmount
```

服务端接口不接受客户端提交的单价、行金额、总金额、优惠金额或应付金额。

预览只表示调用时刻的计算结果，不承诺冻结价格和库存。正式下单必须重新计算。

## 7. 创建订单设计

### 7.1 请求变化

`CreateOrderRequest` 增加必填 `addressId`：

```json
{
  "source": "CART",
  "skuIds": [11, 12],
  "addressId": 1
}
```

### 7.2 地址快照

新增 `order_address_snapshot`：

| 字段 | 说明 |
| --- | --- |
| `order_id` | 订单 ID，一对一唯一 |
| `source_address_id` | 下单时使用的地址 ID，仅供审计 |
| `recipient_name` | 收货人快照 |
| `phone` | 手机号快照 |
| `province` | 省份快照 |
| `city` | 城市快照 |
| `district` | 区县快照 |
| `detail` | 详细地址快照 |
| `created_at` | 快照时间 |

`source_address_id` 不建立阻止地址删除的外键。历史订单详情只读取快照，不实时关联地址簿。

### 7.3 事务顺序

在现有幂等协调器控制的业务事务内：

```text
验证地址归属
→ 重新生成 CheckoutCalculation
→ 校验全部商品
→ 锁定库存
→ 创建订单
→ 创建订单项
→ 创建地址快照
→ 记录订单及行为事件
→ 清理已购买购物车项
→ 完成幂等记录
→ 提交事务
```

任一步骤失败时，订单、订单项、地址快照、库存变化、购物车清理和幂等业务结果一并回滚。

### 7.4 幂等指纹

现有购物车下单指纹从：

```text
cart(normalizedSkuIds)
```

调整为：

```text
cart(normalizedSkuIds, addressId)
```

规则：

- 同一用户、同一幂等键、相同商品和相同地址：返回原订单。
- 同一用户、同一幂等键但商品或地址不同：返回幂等冲突。
- 并发相同请求：只产生一个订单和一个地址快照。

### 7.5 订单响应

订单详情增加地址快照：

```json
{
  "orderNo": "ORD-...",
  "status": "UNPAID",
  "totalAmount": 699.00,
  "items": [],
  "address": {
    "id": 1,
    "recipientName": "林木",
    "phone": "13800000000",
    "province": "浙江省",
    "city": "杭州市",
    "district": "西湖区",
    "detail": "文一路 88 号"
  }
}
```

响应中的地址 ID 是 `source_address_id`；其他字段来自不可变快照。

订单列表不加载完整地址，订单详情才加载快照，避免扩大列表查询。

## 8. 前端设计

现有地址页、结算页、类型和 Mock API 已经提供主要界面基础，本轮以真实契约对齐为主：

- 地址簿增加编辑和设为默认操作。
- 地址新增、修改、删除和默认切换后刷新真实后端结果。
- 结算页正确展示加载、空地址、业务无效原因和 API 错误。
- 创建订单继续提交 `addressId`，不提交金额。
- 每次用户主动提交生成一个稳定的 `Idempotency-Key`。
- 网络超时后的自动或手动重试沿用原键。
- 用户修改地址或商品选择后生成新的键。
- 防止按钮重复点击产生多个逻辑提交。
- 删除只存在于 Mock 实现中的非真实业务假设。

## 9. Docker 设计

### 9.1 Java 镜像

新增：

```text
backend/Dockerfile
backend/.dockerignore
```

采用 Maven/JDK 21 构建和 JRE 21 运行的多阶段镜像。运行镜像：

- 只包含应用 JAR 和运行时。
- 使用非 root 用户。
- 通过环境变量读取连接信息和令牌。
- 使用 `/actuator/health/liveness` 与 `/actuator/health/readiness`。
- 启动时由 Flyway 执行数据库迁移。

### 9.2 Python 镜像

在 Python 仓库新增：

```text
Dockerfile
.dockerignore
```

使用 Python 3.13 slim，以非 root 用户运行：

```bash
uvicorn clothing_assistant.api.app:app --host 0.0.0.0 --port 8000
```

健康检查使用 `GET /health`。模型 Key、内部令牌和 PostgreSQL DSN 均通过环境变量注入。

### 9.3 前端镜像

新增：

```text
frontend/Dockerfile
frontend/.dockerignore
frontend/nginx.conf
```

Node 阶段执行 `npm ci` 和 `npm run build`，Nginx 阶段：

- 提供静态资源。
- 将 SPA 路由回退到 `index.html`。
- 将 `/api/*` 代理到 `java:8080`。
- 为 SSE 路径关闭代理缓冲。
- 提供 `/healthz`。

前端运行时使用同源 `/api`，不把 Java 地址固化进构建产物。

### 9.4 Compose 服务

默认命令：

```bash
docker compose up --build
```

默认启动：

```text
mysql
redis
rabbitmq
langgraph-postgres
python
java
frontend
```

可选搜索实验：

```bash
docker compose --profile search up --build
```

额外启动 Elasticsearch 和 Kibana。搜索服务默认不启动，因为 Java 商品搜索当前默认关闭，且两个容器会显著增加本地资源消耗。

### 9.5 容器连接

Java：

```text
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/intelligent_outfit...
SPRING_DATA_REDIS_HOST=redis
SPRING_RABBITMQ_HOST=rabbitmq
APP_AI_PYTHON_BASE_URL=http://python:8000
```

Python：

```text
LANGGRAPH_CHECKPOINTER_BACKEND=postgres
LANGGRAPH_CHECKPOINTER_DSN=postgresql://...@langgraph-postgres:5432/langgraph
```

Java 和 Python 使用同一个由 `.env` 或 CI Secret 注入的内部服务令牌。

### 9.6 健康检查

| 服务 | 检查 |
| --- | --- |
| MySQL | `mysqladmin ping` |
| Redis | `redis-cli ping` |
| RabbitMQ | `rabbitmq-diagnostics -q ping` |
| PostgreSQL | `pg_isready` |
| Python | `GET /health` |
| Java | Actuator readiness |
| 前端 | `GET /healthz` |

`depends_on` 只负责初始启动顺序，应用仍需保留运行期重试、超时和降级。

### 9.7 配置安全

- 提交 `.env.example`，不提交 `.env`。
- 不提交模型 Key、真实密码和真实内部令牌。
- 应用日志写 stdout/stderr。
- 数据库存储使用命名卷。
- 默认应用容器不挂载源代码。
- `.git`、IDE 文件、虚拟环境、`node_modules` 和测试缓存不进入镜像。

### 9.8 Compose 验证

增加 `scripts/verify-compose.ps1`，负责：

1. 构建应用镜像。
2. 启动核心服务。
3. 等待健康检查。
4. 验证前端、Java 和 Python 健康端点。
5. 执行最小 API smoke test。
6. 失败时输出容器状态和日志。
7. CI 中始终执行 `docker compose down -v`。

## 10. CI 设计

### 10.1 Java/React PR CI

拆分并行任务：

```text
backend-verify
frontend-verify
container-build
```

`backend-verify`：

```bash
cd backend
./mvnw verify
```

`frontend-verify`：

```bash
cd frontend
npm ci
npm test -- --run
npm run build
```

`container-build` 构建 Java 和前端镜像，但不推送。

### 10.2 Python PR CI

保留并明确：

```text
compileall
Ruff
interrogate
unittest discover
Python 镜像构建
```

### 10.3 通用 CI 优化

- Maven、npm 和 pip 使用官方缓存。
- 为 Job 设置合理超时。
- 使用 `concurrency` 取消同一分支的旧运行。
- 使用路径过滤避免纯文档改动触发昂贵任务。
- 每个技术栈独立显示结果。
- PR 只构建镜像，不推送。

### 10.4 跨服务工作流

Java/React 仓库新增：

```text
.github/workflows/cross-service.yml
```

触发：

- 主分支 push。
- 每日定时。
- 手动 `workflow_dispatch`。

流程：

1. 检出 Java/React 仓库。
2. 将 Python 仓库检出到 `_services/python`。
3. 设置 `PYTHON_BUILD_CONTEXT=./_services/python`。
4. 启动核心 Compose。
5. 等待全部服务健康。
6. 执行黄金链路。
7. 上传脱敏日志和测试报告。
8. 无论成功失败都清理容器和卷。

本地 Compose 默认从兄弟目录构建 Python；CI 用环境变量覆盖构建上下文。

跨仓库版本必须固定到记录过兼容性的分支或提交 SHA。Python 主分支的任意变化不能在没有兼容性确认时随机改变 Java 主仓库结果。

## 11. 真实黄金链路

黄金链路不拦截浏览器/API 请求，不使用 MockMvc、假的 Python HTTP 服务或 H2：

```text
注册用户
→ 登录并获取 JWT
→ 新增收货地址
→ 调用真实 Java → Python 推荐链路
→ 从 Java 候选集中选择真实 SKU
→ 加入购物车
→ 结算预览
→ 创建订单
→ 调用项目支付沙箱或回调模拟器
→ 查询支付状态
→ 查询订单状态
→ 验证商品和地址快照
```

真实启动：

```text
Nginx
Java
Python
MySQL
Redis
RabbitMQ
PostgreSQL
```

不接入真实第三方支付平台。

### 11.1 AI 稳定性

- Python 使用真实 FastAPI 和真实推荐编排。
- Java 向 Python 发送真实候选商品。
- 使用确定性种子数据和稳定提示词。
- 关闭不必要的外部 LLM 偏好映射。
- 断言候选来源、结构契约和受控结果，不逐字断言自然语言。
- 外部模型在线 smoke test 可以单独手动或定时运行，但不阻断普通合并。

### 11.2 黄金链路断言

- 注册用户真实写入 MySQL。
- 地址只能由所属用户使用。
- 越权地址无法预览或下单。
- Python 推荐商品 ID 必须来自 Java 候选集。
- 价格和数量来自服务端事实。
- 下单金额来自正式下单时的重新计算。
- 订单拥有独立地址快照。
- 相同幂等键重复创建只存在一个订单。
- 支付重复回调只完成一次有效状态迁移。
- 订单与支付最终状态一致。
- 必要 Outbox、发布或消费记录达到预期状态。

### 11.3 失败证据

CI 失败时上传：

```text
docker compose ps
Java 日志
Python 日志
Nginx 日志
黄金链路测试报告
必要的数据库诊断结果
```

所有证据必须脱敏，不包含 `.env`、JWT、内部令牌、密码或模型 Key。

## 12. 测试策略

### 12.1 地址

- Mapper 测试覆盖用户归属和条件更新。
- Service 测试覆盖首地址默认、默认切换和默认删除。
- Controller 测试覆盖认证、参数和响应。
- MySQL 并发测试确保最终最多一个默认地址。

### 12.2 结算

- 服务端金额计算。
- 购物车数量可信。
- 商品下架和库存不足。
- 地址越权。
- 不存在接收客户端金额的接口字段。
- 预览和正式下单共享相同计算规则。

### 12.3 订单

- 地址快照与订单同事务写入。
- 地址进入幂等指纹。
- 相同键并发只产生一单和一份快照。
- 相同键更换地址返回冲突。
- 失败时订单、快照、库存和购物车变更全部回滚。
- 地址修改或删除后历史订单响应不变。

### 12.4 前端

- 地址新增、编辑、删除和默认切换。
- 空地址和预览错误状态。
- 业务无效原因展示。
- 重复点击不产生多个逻辑请求。
- 同一次重试复用幂等键。
- 修改商品或地址后使用新键。

### 12.5 容器与 CI

- 三个应用镜像可以从干净上下文构建。
- Compose 可以从空卷启动并完成 Flyway 迁移。
- 所有核心健康检查通过。
- 黄金链路至少连续成功三次，证明可重复性。

## 13. 实施顺序与提交边界

每个步骤完成测试后单独提交，不伪造历史：

1. `test: define address ownership and checkout acceptance`
2. `feat: add user-owned address management`
3. `feat: add server-calculated checkout preview`
4. `test: cover checkout price stock and ownership boundaries`
5. `feat: persist order address snapshots`
6. `fix: include address in order idempotency fingerprint`
7. `test: verify concurrent idempotent checkout with mysql`
8. `feat: connect checkout pages to real backend`
9. `build: add java python and frontend images`
10. `build: start the complete system with compose`
11. `test: add real cross-service shopping flow`
12. `ci: split fast checks and cross-service verification`

提交名称可以按实际改动调整，但每次提交只表达一个可验证目的。

## 14. 分阶段验收

### 阶段 A1：可信结算

- 地址 CRUD 和默认地址规则可用。
- 所有地址操作具有用户归属保护。
- 结算预览由服务端计算。
- 正式下单重新计算。
- 订单保存地址快照。
- 现有幂等能力覆盖地址变化。
- Java 和前端相关测试通过。

### 阶段 A2：应用容器化

- 三个应用 Dockerfile 构建成功。
- `docker compose up --build` 启动核心系统。
- 从空数据库自动迁移。
- 所有健康检查通过。
- 不配置真实模型 Key 时，基础商城仍可用且 AI 受控降级。
- `search` profile 可选启用且不影响默认链路。

### 阶段 A3：CI 与黄金链路

- PR CI 分别显示 Java、Python、前端和镜像构建结果。
- 跨服务工作流真实启动两个仓库和基础设施。
- 黄金链路不使用 HTTP Mock。
- 相同环境连续至少三次运行结果一致。
- 失败时有可定位且脱敏的日志。

## 15. 风险与控制

### 15.1 预览与下单之间发生变化

预览不冻结价格和库存。正式下单重新计算并返回明确错误，前端随后刷新预览。

### 15.2 默认地址并发竞争

默认切换在事务和锁保护下完成，并使用 MySQL 并发测试验证最终唯一性。

### 15.3 跨仓库版本漂移

跨服务 CI 固定兼容的 Python revision。升级 revision 时先运行契约和黄金链路。

### 15.4 Compose 资源过重

Elasticsearch/Kibana 移入 `search` profile，默认黄金链路只启动必要服务。

### 15.5 外部模型波动

阻断式黄金链路验证真实 Python 推荐编排和候选边界，但不依赖外部模型逐字输出。外部模型 smoke test 单独运行。

### 15.6 现有未提交文件

实施时只修改设计列出的相关文件，不覆盖当前仓库中已有的未提交学习测试、IDE 配置或其他用户工作。

## 16. 完成定义

只有同时满足以下条件，才可以宣称本轮完成：

- A1、A2、A3 的验收项全部通过。
- Java 执行 `mvnw.cmd verify` 成功。
- Python全量质量检查成功。
- 前端 Vitest 和生产构建成功。
- 三个镜像从干净上下文构建成功。
- Compose 从空卷成功启动。
- 黄金链路连续至少成功三次。
- CI 配置已在实际 GitHub Actions 环境运行，而不仅是本地语法检查。
- 文档、环境变量示例和实际行为一致。
