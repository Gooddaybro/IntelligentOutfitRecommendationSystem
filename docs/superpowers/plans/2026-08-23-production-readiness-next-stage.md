# 下一阶段生产化实施计划（Production Readiness Next Stage）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不重做现有 Docker、不拆分模块化单体的前提下，先恢复 Java/Python 共享契约和测试基线，再打通地址、可信结算、订单快照、真实前后端与无 HTTP Mock 黄金链路，最终建立可持续的 CI、生产安全与运维门禁。

**Architecture:** Java 继续作为用户、地址、商品、价格、库存、购物车、订单、支付和前端 API 的唯一事实源；Python 负责意图识别、LangGraph 编排、RAG、排序解释和自然语言生成；React 只提交用户选择，不提交交易金额；MySQL 保存交易最终事实，Redis、Elasticsearch、RabbitMQ、PostgreSQL 保持现有职责。下一阶段按一条纵向依赖链推进，不同时铺开互相依赖的业务模块。

**Tech Stack:** Java 21、Spring Boot、MyBatis、Flyway、MySQL、Redis、RabbitMQ、Elasticsearch、Python 3、FastAPI、Pydantic、LangGraph、PostgreSQL、React、TypeScript、Vitest、Playwright、Docker Compose、GitHub Actions、Prometheus、Grafana。

## Global Constraints

- 现有 Java、Python、前端 Dockerfile 和 Compose 已基本落地；本计划只验证、修正生产配置和 CI 编排，不重新设计容器化。
- Java 是交易事实源。Python、前端都不得创建或覆盖价格、库存、地址归属、订单状态、支付状态。
- 不新增微服务、Kafka、Kubernetes、分布式事务框架、结算缓存或结算令牌。
- 第一版结算固定 `shippingAmount = 0`、`discountAmount = 0`、`payableAmount = merchandiseAmount`，不伪造优惠和运费能力。
- 外部 Kimi/Jina 只能位于 Provider Adapter 后方；单元测试和阻断式 CI 使用确定性替身，禁止意外访问真实供应商。
- Mock 支付继续明确标记为 Mock；真实支付在本计划最后单独立项，完成官方验签、退款、对账和补偿前不得描述为生产支付。
- 每个阶段先写失败测试，再完成最小实现，再运行阶段门禁；前一阶段未通过，不进入下一阶段。
- 所有新增 Java 顶层类型和核心边界方法遵守 `docs/commenting-guidelines.md`，说明责任边界而不是复述代码。
- 每个提交只表达一个可验证目的，不混入无关重构、依赖升级或 UI 改版。

---

## 1. 结论与优先级

### 1.1 推荐顺序

| 顺序 | 优先级 | 阶段 | 阻断下一阶段的原因 |
| --- | --- | --- | --- |
| 1 | P0 | 共享契约与 Python 测试基线 | 协议错误未消除时，业务错误和跨服务错误无法可靠区分 |
| 2 | P0 | 用户地址模块 | 地址归属和地址事实是结算、订单快照的前置条件 |
| 3 | P0 | 服务端结算预览 | 建立唯一金额计算边界，防止预览与下单规则漂移 |
| 4 | P0 | 可信结算接入订单 | 形成交易事务、地址快照和幂等一致性 |
| 5 | P0 | 清理前后端接口假闭环 | 页面展示成功不能替代真实接口可运行 |
| 6 | P0 | 无 HTTP Mock 黄金链路 | 证明 React、Java、Python、MySQL 和基础设施组合兼容 |
| 7 | P0 发布门禁 | 分层 CI 与版本关系 | 把一次性成功固化成持续、可重复的组合版本证据 |
| 8 | P1 上线门禁 | 生产安全与运维 | 解决公开部署后的密钥、权限、恢复和可观测性风险 |
| 9 | P2 | 真实支付 | 依赖前面全部交易和运维门禁，不应提前引入外部资金风险 |

### 1.2 依赖关系

```text
共享契约绿色
  → 地址归属正确
  → CheckoutCalculator 成为唯一金额规则
  → 订单快照 + 库存 + 购物车 + 幂等同事务
  → 前后端真实接口对齐
  → 无 HTTP Mock 黄金链路
  → 跨仓库固定版本 CI
  → 生产安全与运维
  → 真实支付
```

### 1.3 本轮明确不重做的内容

- 不重写 Java、Python、前端 Dockerfile。
- 不把现有 Java 模块化单体拆成地址、结算、订单微服务。
- 不替换 MyBatis、Flyway、Spring Security 或现有库存应用服务。
- 不为了“架构更复杂”引入额外中间件。
- 不把现有 Mock E2E 删除；它继续承担快速 UI 回归，但不再代表真实集成通过。

---

## 2. 仓库职责与统一门禁

### 2.1 仓库职责

| 仓库 | 职责 | 本阶段主要改动 |
| --- | --- | --- |
| `outfit-project-contract` | 跨语言字段清单和 RAG/MQ JSON Schema | 契约字段、三份 RAG rebuild Schema、版本说明 |
| `AI-Clothing-Shopping-Assistant-System` | Python AI、FastAPI、Pydantic、LangGraph、Provider Adapter | v3 DemandIntent、`rejected_reasons`、pytest、Embedding 测试替身 |
| `IntelligentOutfitRecommendationSystem/backend` | 用户、地址、商品、结算、订单、支付事实 | 地址、CheckoutCalculator、地址快照、幂等指纹、真实接口 |
| `IntelligentOutfitRecommendationSystem/frontend` | 用户交互和交易选择 | 真实地址/结算/下单、幂等键、接口清理、黄金链路 |
| `IntelligentOutfitRecommendationSystem` | Compose 与跨服务集成入口 | 分层 CI、兼容 Python revision、日志证据、生产配置 |

### 2.2 每个阶段都要遵守的完成定义

- [ ] 新增行为有至少一个先失败、后通过的测试。
- [ ] 测试不访问真实 Kimi/Jina，不依赖开发者机器已有数据。
- [ ] API 的身份只来自 JWT/服务端上下文，不接受请求体 `userId`。
- [ ] 涉及金额、库存、地址归属时，断言数据来自 Java/MySQL 事实。
- [ ] 错误响应不泄露其他用户资源是否存在。
- [ ] 文档、契约、实现和测试在同一阶段同步更新。
- [ ] 阶段门禁全部通过后才允许开始下一阶段。

### 2.3 全阶段最终验证命令

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
PYTHON_DOTENV_DISABLED=1 .venv/bin/python -m pytest -q
.venv/bin/ruff check clothing_assistant tests
.venv/bin/python -m compileall -q clothing_assistant tests

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp verify

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/frontend
npm test -- --run
npm run build
npm run test:e2e

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem
docker compose -f docker-compose.yml -f docker-compose.demo.yml config --quiet
```

---

## 3. 阶段 1：修复共享契约和 Python 测试基线

**优先级：** P0，所有后续开发的入口门禁。

**阶段目标：** Java DTO、Python Pydantic 模型、共享字段清单和同步/SSE 客户端对同一份契约达成一致；Python 全量测试无需真实供应商即可运行。

### Task 1.1：先用契约测试固定预期

**Files:**

- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_shared_contract.py`
- Verify: `/Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java`
- Modify: `/Users/seekinward/Documents/推荐项目/outfit-project-contract/contracts/java-python-chat/v1.fields.json`

**Interfaces:**

- Java → Python request: `POST /chat`
- Java → Python stream: `POST /chat/stream`
- Python response field: `rejected_reasons: dict[str, int]`
- Demand contract version: `demand-intent-v3`

- [x] 在 Python 契约测试中断言 `java_consumed_chat_response ⊆ python_chat_response`。
- [x] 在 Python 契约测试中断言 `PythonChatResponse.model_fields` 正式包含 `rejected_reasons`。
- [x] 在 Java 契约测试中保留 `PythonChatResponse` 与 `java_consumed_chat_response` 的精确字段比较。
- [x] 在共享字段清单的 `python_chat_response` 中加入 `rejected_reasons`，保留 `suggested_actions` 和 `debug` 作为 Python 可发送但 Java 当前不消费的字段。
- [x] 将契约 `updated_on` 更新为本阶段实际完成日期；只有发生不兼容变更时才提升主版本，本次加法字段保持向后兼容。
- [x] 先运行两侧契约测试，记录其因 Python 模型缺字段、v3 类型缺失而失败。

**预期失败：** Python 无法导入 `IntentConstraint`/`SubjectMeasurements`，或字段集合与共享清单不一致。

### Task 1.2：让 PythonChatResponse 正式包含 rejected_reasons

**Files:**

- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/schemas.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/application/answer_service.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_api.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_chat_stream.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_shared_contract.py`

- [x] 给 `PythonChatResponse` 增加 `rejected_reasons: dict[str, int] = Field(default_factory=dict, ...)`。
- [x] `build_agent_response` 从工作流结果中读取聚合拒绝原因；没有数据时返回空对象，不返回 `null`。
- [x] 同步 `/chat` 响应测试断言字段存在且值正确。
- [x] `/chat/stream` 的最终结构化事件测试断言同一字段存在，避免同步与流式漂移。
- [x] 不把内部异常、候选详情或提示词写入 `rejected_reasons`；只允许有限、稳定的原因代码及数量。

### Task 1.3：按 Java EffectiveDemand 对齐 demand-intent-v3

**Files:**

- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/schemas.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_shared_contract.py`
- Verify: `/Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/EffectiveDemand.java`
- Verify: `/Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/IntentConstraint.java`
- Verify: `/Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/SubjectMeasurements.java`

**Target shape:**

```json
{
  "version": "demand-intent-v3",
  "requestType": "OUTFIT_ADVICE",
  "requestedCapabilities": ["RECOMMENDATION"],
  "hardFilters": [],
  "softPreferences": [],
  "subjectMeasurements": null
}
```

- [x] 新增 Python `IntentConstraint`，字段精确对应 `id`、`field`、`operator`、`values`、`strength`、`origin`、`originTurnId`、`derivedFromConstraintId`、`scope`、`weight`。
- [x] 新增 Python `SubjectMeasurements`，字段精确对应 `heightCm`、`weightKg`、`originalText`、`normalizedFrom`、`subject`、`scope`、`source`。
- [x] `DemandIntent` 正式支持 v3 的 `requestType`、`requestedCapabilities`、`hardFilters`、`softPreferences`、`subjectMeasurements`。
- [x] 枚举值与 Java `ConstraintOperator`、`ConstraintStrength`、`ConstraintOrigin` 保持一致；未知枚举在边界处拒绝。
- [x] 硬约束禁止携带权重；派生约束必须携带父约束 ID；非派生约束不得携带父约束 ID。
- [x] `hardFilters` 只能接收 `strength=HARD`，`softPreferences` 只能接收 `strength=SOFT`。
- [x] 保留现有 v2 字符串约束列表的只读兼容，确保滚动升级期间旧请求不会立即失败；新请求和新测试统一使用 v3。
- [x] 不在 Python 中复制 Java 的商品过滤决策；Python 只读取约束用于排序、解释和追问。

### Task 1.4：补齐 RAG rebuild 的三份 JSON Schema

**Files:**

- Create: `/Users/seekinward/Documents/推荐项目/outfit-project-contract/contracts/rag-rebuild/schemas/rag-rebuild-request.schema.json`
- Create: `/Users/seekinward/Documents/推荐项目/outfit-project-contract/contracts/rag-rebuild/schemas/rag-rebuild-response.schema.json`
- Create: `/Users/seekinward/Documents/推荐项目/outfit-project-contract/contracts/rag-rebuild/schemas/ai-task-requested.schema.json`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_rag_rebuild_contract.py`

- [x] 使用 JSON Schema 2020-12，根对象设置 `type: object` 和 `additionalProperties: false`。
- [x] request 必填 `taskId`、`source`；`source` 只允许当前真实值 `LOCAL_GLOBAL_KNOWLEDGE`。
- [x] response 必填 `taskId`、`indexVersion`、`fileCount`、`chunkCount`、`contentDigest`、`replayed`，数量字段为非负整数。
- [x] MQ event 必填 `eventId`、`eventType`、`schemaVersion`、`taskId`、`taskType`、`occurredAt`、`correlationId`、`traceparent`。
- [x] Schema 值与 Java/Python 已存在的 RAG rebuild DTO 和事件生产者一致，不新增实现中不存在的字段。
- [x] 测试同时验证必填字段和非法额外字段，避免 Schema 只存在但不具备约束能力。

### Task 1.5：统一 Python 测试入口并隔离 Embedding Provider

**Files:**

- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/requirements-dev.txt`
- Create: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/fakes.py`
- Create: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/conftest.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_recommendation_service.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_vector_store.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_rag_tool.py`
- Verify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_api.py`

- [x] 将与当前 Python 版本兼容的 `pytest` 固定到开发依赖范围，不放入生产依赖。
- [x] 统一本地和 CI 测试入口为 `.venv/bin/python -m pytest -q`。
- [x] 实现确定性的 Fake Embedding Adapter：相同文本始终产生相同、固定维度向量，同时记录 `embed_documents`/`embed_query` 调用。
- [x] 在测试 fixture 中通过应用已有 Adapter seam 注入 fake；不 patch `requests` 来掩盖真实对象被错误创建的问题。
- [x] 增加“测试期间网络被调用即失败”的保护断言，防止 Jina Key 存在时测试悄悄走真实网络。
- [x] Jina Adapter 自身的单测继续 mock 其 HTTP 边界，只验证 task、timeout、排序和错误归一化。
- [x] 清理全局 embedding cache，保证测试顺序不会影响结果。

### 阶段 1 验收

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
PYTHON_DOTENV_DISABLED=1 .venv/bin/python -m pytest -q
.venv/bin/ruff check clothing_assistant tests
.venv/bin/python -m compileall -q clothing_assistant tests

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp \
  -Dtest=SharedJavaPythonContractTests,RestPythonAssistantClientTests,PythonSseEventParserTests,AssistantControllerTests \
  test
```

**完成条件：** Python 全量测试、Java 共享契约测试、同步接口测试和流式接口测试全部通过；测试日志中不存在对真实 Jina/Kimi 的访问。

**为什么必须先做：** 这是后续所有跨服务功能的坐标系。契约为红色时继续写结算和 E2E，会把协议错误、业务错误和环境错误叠加在一起，定位成本最高。

**面试表达：**

> 我没有先堆新功能，而是先建立跨语言契约门禁。共享字段清单同时约束 Java DTO 和 Pydantic 模型，同步与 SSE 也跑同一契约；外部 Embedding 通过 Adapter 注入确定性替身，所以两个仓库单独绿色和组合运行绿色表达的是同一件事。

**建议提交边界：**

1. `test: define java python v3 contract gate`
2. `fix: align python chat response and demand intent v3`
3. `fix: publish rag rebuild schemas`
4. `test: isolate embedding provider and unify pytest`

---

## 4. 阶段 2：实现用户地址模块

**优先级：** P0，可信结算的前置事实。

**Public APIs:**

```http
GET    /api/addresses
POST   /api/addresses
PUT    /api/addresses/{addressId}
DELETE /api/addresses/{addressId}
PUT    /api/addresses/{addressId}/default
```

### Task 2.1：数据库与领域模型

**Files:**

- Create: `backend/src/main/resources/db/migration/V26__address_checkout_schema.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/model/UserAddress.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/mapper/AddressMapper.java`
- Create: `backend/src/main/resources/mapper/address/AddressMapper.xml`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`

- [x] 先写 Flyway 测试，断言 `user_address`、后续使用的 `order_address_snapshot` 结构和索引可以在空 MySQL 上迁移。
- [x] `user_address` 保存 `user_id`、收货人、手机号、省、市、区县、详细地址、`is_default`、创建和更新时间。
- [x] 同一份 V26 迁移预建 `order_address_snapshot`，避免阶段 4 修改已经在环境中执行过的 Flyway 文件；阶段 2 暂不从业务代码访问该表。
- [x] 建立 `(user_id, is_default)` 与 `(user_id, updated_at)` 索引。
- [x] Mapper 更新、删除、查单条全部使用 `address_id + current_user_id`；禁止先按 ID 查询再在 Java 中判断所有者。
- [x] Mapper 提供按用户清除默认地址、设置默认地址和选择删除后候补默认地址的最小 SQL。

### Task 2.2：DTO、Service 与 Controller

**Files:**

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/dto/AddressSaveRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/dto/AddressResponse.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/service/AddressService.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/address/api/AddressController.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/address/AddressServiceTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/address/AddressControllerTests.java`

- [x] Controller 从 `CurrentUser.from(authentication)` 获取用户 ID，请求体不包含 `userId`。
- [x] 校验收货人、手机号、省、市、区县、详细地址非空，并设置与数据库列一致的长度限制。
- [x] 首个地址自动默认，即使客户端未提交默认标记。
- [x] 设置默认地址在同一个事务内锁定该用户地址集合、清除旧默认、设置新默认。
- [x] 删除默认地址后，把剩余地址中最近更新的一条设为默认；删除最后一个地址后允许无默认地址。
- [x] 不存在和不属于当前用户返回相同公开语义，例如统一 `ADDRESS_NOT_FOUND`，不泄露其他用户地址是否存在。
- [x] 列表默认按 `is_default DESC, updated_at DESC, id DESC` 返回，避免前端自行猜测默认项。

### Task 2.3：所有权和默认地址并发测试

**Files:**

- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/address/AddressOwnershipMySqlTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/address/DefaultAddressMySqlConcurrencyTests.java`

- [x] 用户 A 无法读取、修改、删除或设为默认用户 B 的地址。
- [x] 用户 A 猜测存在的 B 地址 ID 与随机不存在 ID 得到相同公开错误。
- [x] 两个线程同时把不同地址设为默认，最终查询结果最多一条默认地址。
- [x] 并发测试使用真实 MySQL、独立事务和同步起跑屏障，不使用 H2 模拟锁行为。
- [x] 回滚场景断言旧默认仍然有效，不留下“两个默认”或“意外无默认”。

### 阶段 2 验收

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp \
  -Dtest=AddressServiceTests,AddressControllerTests \
  test
RUN_MYSQL_TESTS=true sh ./mvnw -B -ntp \
  -Dtest=MySqlFlywayMigrationTests,AddressOwnershipMySqlTests,DefaultAddressMySqlConcurrencyTests \
  test
```

**完成条件：** 五个地址接口可用；所有权 SQL 和公开错误语义通过；真实 MySQL 并发后一个用户最多一个默认地址。

**为什么现在做：** 结算必须先回答“这个地址是否真实属于当前用户”。如果把归属判断推迟到下单阶段，预览接口本身就会形成越权探测面。

**面试表达：**

> 地址接口不接收 userId，而是从 JWT 上下文拿当前用户。所有更新和删除 SQL 都同时带 addressId 与 currentUserId；不存在和越权使用同一错误语义，既防止水平越权，也避免泄露其他用户资源是否存在。默认地址切换通过事务和真实 MySQL 并发测试保证最终唯一。

**建议提交边界：**

1. `test: define address ownership and default rules`
2. `feat: add user owned address management`
3. `test: verify default address concurrency with mysql`

---

## 5. 阶段 3：实现服务端结算预览

**优先级：** P0，交易金额唯一规则源。

**Public API:**

```http
POST /api/checkout/preview
```

**Request:**

```json
{
  "skuIds": [11, 12],
  "addressId": 1
}
```

**Response:**

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

### Task 3.1：建立深模块 CheckoutCalculator

**Files:**

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/service/CheckoutCalculator.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/model/CheckoutCalculation.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/model/CheckoutCalculatedItem.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/model/CheckoutInvalidReason.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/mapper/CheckoutMapper.java`
- Create: `backend/src/main/resources/mapper/checkout/CheckoutMapper.xml`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/CheckoutCalculatorTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/CheckoutMapperTests.java`

**Internal interfaces:**

```java
CheckoutCalculation previewCart(Long userId, List<Long> skuIds, Long addressId)
CheckoutCalculation calculateForOrder(Long userId, List<Long> skuIds, Long addressId)
```

- [x] 先写测试覆盖地址归属、购物车数量、商品上下架、当前 SKU 售价、库存不足和金额精度。
- [x] 将现有 `OrderMapper.findCheckoutItemsFromCart` 的联合查询迁入 `CheckoutMapper`，让读取交易事实和计算规则都隐藏在 checkout 模块；阶段 4 切换订单后删除旧查询。
- [x] 两个入口共享一个私有读取/计算流程，不复制金额公式。
- [x] `previewCart` 把库存不足、商品失效写入 `invalidReasons`；认证失败、参数非法、地址越权仍抛标准 API 错误。
- [x] `calculateForOrder` 遇到同一业务无效原因直接拒绝，不返回可继续创建订单的结果。
- [x] 数量来自服务端购物车，商品状态和价格来自 MySQL，库存来自现有库存查询边界。
- [x] 金额全程使用 `BigDecimal`，在领域边界统一小数位和舍入规则。
- [x] `skuIds` 只表示用户选择的购物车项，不允许请求携带数量、单价、折扣或总金额。
- [x] 模块对外只暴露两个业务入口和不可变计算结果，隐藏 Mapper 拼装与规则细节。

### Task 3.2：暴露结算预览接口

**Files:**

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/dto/CheckoutPreviewRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/dto/CheckoutPreviewResponse.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/api/CheckoutController.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/checkout/CheckoutControllerTests.java`

- [x] `CheckoutPreviewRequest` 只包含非空 `skuIds` 和必填 `addressId`。
- [x] Controller 从 JWT 上下文取 `userId`，调用 `previewCart`，不直接读取 Mapper。
- [x] 响应 item 包含 SKU、服务端数量、当前单价、行金额和可展示的商品快照字段。
- [x] 测试证明请求体中即使出现未知金额字段也不会成为计算输入；采用拒绝未知字段或完全忽略但永不读取的单一策略并写入契约测试。
- [x] 预览响应明确是时点计算结果，不生成价格锁或库存锁。

### 阶段 3 验收

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp \
  -Dtest=CheckoutCalculatorTests,CheckoutMapperTests,CheckoutControllerTests \
  test
```

**完成条件：** 预览和正式下单准备路径共享同一计算模块；客户端无法影响价格、数量和总金额；库存不足能在预览中解释、在正式计算中阻断。

**为什么现在做：** 如果预览和下单各写一套金额逻辑，任何一次价格、运费或优惠规则修改都可能产生双重事实。先建立深模块，可以让未来规则只改一处。

**面试表达：**

> 我把结算做成 CheckoutCalculator 深模块，购物车读取、商品状态、当前价格、库存和金额公式都隐藏在模块内部。预览不是价格承诺，正式下单会重新计算；客户端只提交商品选择和地址，任何金额字段都由服务端产生。

**建议提交边界：**

1. `test: define trusted checkout calculation rules`
2. `feat: add server calculated checkout preview`

---

## 6. 阶段 4：把可信结算接入订单

**优先级：** P0，完成核心交易闭环。

### Task 4.1：订单请求和地址快照

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/CreateOrderRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/OrderAddressSnapshot.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/dto/OrderResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/mapper/OrderMapper.java`
- Modify: `backend/src/main/resources/mapper/order/OrderMapper.xml`
- Verify: `backend/src/main/resources/db/migration/V26__address_checkout_schema.sql`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderMapperTests.java`
- Delete after migration: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/model/OrderCheckoutItem.java`

- [x] `CreateOrderRequest` 增加必填 `addressId`，仍不接受金额字段。
- [x] `order_address_snapshot` 与订单一对一，保存 `source_address_id` 和下单时完整地址文本。
- [x] `source_address_id` 仅供审计，不建立阻止用户删除地址的强外键。
- [x] 订单详情读取快照；订单列表不额外加载完整地址，避免列表查询膨胀。
- [x] 修改或删除地址簿地址后，历史订单详情保持不变。

### Task 4.2：正式下单事务使用 CheckoutCalculator

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/TrustedCheckoutMySqlIntegrationTests.java`

**Required transaction order:**

```text
验证地址归属
→ calculateForOrder 重新计算
→ 锁定库存
→ 创建订单
→ 创建订单项
→ 创建地址快照
→ 记录订单行为/事件
→ 清理已购买购物车项
→ 完成幂等结果
→ 提交事务
```

- [x] 正式下单不复用前端预览金额，而是调用 `calculateForOrder`。
- [x] `OrderService` 不再直接读取 `OrderMapper.findCheckoutItemsFromCart` 或自行计算总额；删除旧 Mapper 查询和 `OrderCheckoutItem`，只消费 checkout 模块的不可变结果。
- [x] 订单总额和订单项价格全部取自 `CheckoutCalculation`。
- [x] 地址、订单、订单项、地址快照、库存变化、购物车清理、订单事件和幂等业务结果位于同一事务；严格事件写入失败会触发整笔交易回滚。
- [x] 在“库存已锁定但快照写入失败”场景注入故障，断言所有变化回滚。
- [x] 保留现有 `InventoryApplicationService` 的锁定/确认/释放边界，不新增一套库存实现。

### Task 4.3：扩展幂等指纹并接入前端 Idempotency-Key

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderRequestFingerprint.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderIdempotencyCoordinator.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderRequestFingerprintTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyCoordinatorTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderIdempotencyMySqlConcurrencyTests.java`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/pages/CheckoutPage.tsx`
- Modify: `frontend/src/pages/CheckoutPage.test.tsx`

- [x] 购物车订单指纹从 `normalizedSkuIds` 扩展为 `normalizedSkuIds + addressId`。
- [x] 相同用户、相同键、相同商品和地址返回原订单。
- [x] 相同用户和键但商品或地址变化返回幂等冲突，不错误复用旧订单。
- [x] 并发相同请求只生成一个订单、一个地址快照和一次有效库存变化。
- [x] 前端每次用户主动提交生成 UUID `Idempotency-Key`，购物车下单和立即购买均已接入。
- [x] 同一次网络重试复用原键；用户修改地址、商品或数量，或者取消后重新发起时生成新键。
- [x] 提交按钮防重复点击，但按钮防抖不能替代服务端幂等。

### 阶段 4 验收

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp \
  -Dtest=OrderServiceTests,OrderRequestFingerprintTests,OrderIdempotencyCoordinatorTests \
  test
RUN_MYSQL_TESTS=true sh ./mvnw -B -ntp \
  -Dtest=TrustedCheckoutMySqlIntegrationTests,OrderIdempotencyMySqlConcurrencyTests \
  test

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/frontend
npm test -- --run
npm run build
```

**完成条件：** 下单重新计算、保存不可变地址快照、地址进入幂等指纹；事务任一点失败都不会留下半成品订单、库存或购物车状态。

**为什么现在做：** 地址簿是可变数据，订单是历史事实。只保存地址 ID 会让历史订单随地址簿变化；不把地址加入幂等指纹则可能在用户换地址后错误复用旧订单。

**面试表达：**

> 我保存的是下单时的地址快照，不是对地址簿的实时引用。正式下单重新计算价格和库存，并把订单、订单项、地址快照、库存、购物车清理和幂等结果放在同一事务中。同一个幂等键如果地址或商品变化会返回冲突，而不是错误复用旧订单。

**建议提交边界：**

1. `feat: persist immutable order address snapshot`
2. `refactor: create orders from checkout calculation`
3. `fix: include address in order idempotency fingerprint`
4. `test: verify trusted checkout rollback and concurrency`

### 阶段 4 完成记录（2026-08-29）

**已提交完成：**

- [x] Task 4.1：订单请求增加 `addressId`，持久化不可变订单地址快照。
- [x] Task 4.2 主体：正式下单统一使用 `CheckoutCalculator`，服务端重新计算金额并纳入订单事务。
- [x] Task 4.3 购物车路径：地址进入幂等指纹，购物车提交支持 UUID 幂等键、失败重试复用和意图变化换键。
- [x] 阶段 4 计划项和主体实现分别提交在 `79ad96c`、`edfa308`、`5137c5c`、`7ea1ab7`。

**最终复审收口：**

- [x] 增加订单严格事件写入入口，使订单事件失败能够触发整笔交易回滚；单测、Spring 代理测试和真实 MySQL 故障注入测试均已通过。
- [x] 正式下单通过一条 `FOR UPDATE OF ci, sku, spu, inv` 当前读同时取得并锁定交易事实；既避免可重复读旧快照，也不锁共享展示维表。
- [x] 立即购买请求增加 UUID `Idempotency-Key`；同一未关闭动作失败重试复用，成功、取消或意图变化后换键。
- [x] 结算页直接展示服务端 `lineAmount`，不再由浏览器重复计算行金额。
- [x] 补充地址持久化前去除首尾空白的边界说明。

**最终验证：**

- [x] 后端阶段 4 针对性测试：42 个测试通过。
- [x] 真实 MySQL 回滚与并发测试：5 个测试通过。
- [x] 前端全量测试：31 个测试文件、84 个测试通过；生产构建成功。
- [x] Java 全量 `mvn verify`：538 个测试，0 失败、0 错误、12 个条件跳过；Checkstyle 0 违规。
- [x] 最终修复经过两轮独立复审，结论为 Critical 0、Important 0、无新增 Minor，Assessment `Yes`。
- [x] `git diff --check` 通过；Python 仓库和 Docker 配置未被本阶段收口改动。

**当前结论：** 阶段 4 已完成最终验收。下一步从阶段 5 的接口假闭环清理开始，不再重复 Task 4.1～4.3。

---

## 7. 阶段 5：清理前后端接口假闭环

**优先级：** P0，黄金链路前的接口收口。

### Task 5.1：收藏接口统一

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/favorite/api/FavoriteController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/favorite/dto/FavoriteAddRequest.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/favorite/service/FavoriteService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/favorite/FavoriteServiceTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/favorite/FavoriteControllerTests.java`
- Modify: `frontend/src/shared/api/client.ts`

**Final APIs:**

```http
GET    /api/favorites
POST   /api/favorites          { "spuId": 1001 }
DELETE /api/favorites/{spuId}
```

- [x] 后端基路径从 `/favorites` 统一为 `/api/favorites`。
- [x] 补列表接口，结果只包含当前用户收藏。
- [x] POST 统一使用 JSON body，DELETE 使用 path parameter；前后端测试采用同一形式。
- [x] 收藏不存在时的删除语义固定并测试，不让 Mock 和真实实现产生两种行为。

**Task 5.1 收口说明：** 收藏接口返回关系优先的商品展示投影；下架或缺货商品仍可见、可删除，价格、库存和可用状态使用一致口径。可选 `recommendationId` 归因继续保留，未知 SPU 返回 404，Mock 会随当前商品状态和库存同步变化。后端针对性测试 22 项、前端 90 项及生产构建通过，独立复审结论为 Critical 0、Important 0、Assessment `Yes`。

### Task 5.2：确认收货能力做明确取舍

**Files:**

- Verify: `frontend/src/shared/api/client.ts`
- Verify: `frontend/src/pages/OrderDetailPage.tsx`
- Verify: `frontend/src/pages/OrderDetailPage.test.tsx`
- Verify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/api/OrderController.java`

- [ ] 若当前阶段需要完整订单生命周期，则实现 `POST /api/orders/{orderNo}/confirm-receipt`，校验当前用户、订单已发货和幂等状态迁移。
- [x] 若当前阶段没有发货状态和运营入口，则从前端移除确认收货按钮和 client 方法，不保留会返回 404 的假入口。
- [x] 本计划默认采用第二种最小方案：先移除前端假入口；真实物流/收货在有发货状态机时单独实现。

**Task 5.2 收口说明：** 已移除订单详情页确认收货按钮、HTTP client 方法和 Mock 假实现；运输中订单仍展示物流信息，管理员发货与取消订单能力不受影响。静态进度条只表达订单阶段，不再触发不存在的接口。前端 91 项测试及生产构建通过，独立复审结论为 Critical 0、Important 0、Assessment `Yes`。

### Task 5.3：地址、结算、下单切换到真实后端

**Files:**

- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/pages/AddressBookPage.tsx`
- Modify: `frontend/src/pages/AddressBookPage.test.tsx`
- Modify: `frontend/src/pages/CheckoutPage.tsx`
- Modify: `frontend/src/pages/CheckoutPage.test.tsx`
- Modify: `frontend/src/shared/api/client.test.ts`
- Preserve: `frontend/e2e/fixtures/api.ts`

- [ ] 地址页面真实调用五个地址接口，包含编辑、删除和设为默认。
- [ ] 结算页面真实调用 `/api/checkout/preview`，展示加载、无地址、`invalidReasons` 和 API 错误。
- [x] 下单只发送 `source`、`skuIds`、`addressId`，并携带 `Idempotency-Key` 请求头。2026-09-09 验收现有实现：client 与 CheckoutPage 共 15 项测试通过，OrderControllerTests 14 项通过；覆盖失败重试复用键、地址或 SKU 改变换键。本项未重复开发，不代表阶段 5 全部完成。
- [ ] Mock 数据模式仅用于组件测试和快速演示，命名和文档明确包含 `mock`，不再叫“集成模式”。
- [ ] 为 client 添加契约测试，断言 URL、method、body 和 header 与后端一致。

### 阶段 5 验收

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -B -ntp -Dtest=FavoriteServiceTests,FavoriteControllerTests,OrderControllerTests test

cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/frontend
npm test -- --run
npm run build
npm run test:e2e
```

**完成条件：** 前端声明的地址、收藏、结算、下单和订单操作都存在对应真实后端；未实现能力不再以按钮或 client 方法伪装为可用。

**为什么现在做：** Mock 可以验证界面交互，但不能验证路由、鉴权、请求体、错误语义和事务。先消除假接口，黄金链路失败时才有明确含义。

**面试表达：**

> 我把 Mock E2E 和真实集成测试分开管理。Mock 只证明组件交互，真实 API 才证明系统可运行；前端暴露但后端不存在的能力要么补齐，要么移除，避免“页面看起来完整”掩盖接口并未闭环。

**建议提交边界：**

1. `fix: align favorite api contract`
2. `fix: remove unsupported order actions`
3. `feat: connect checkout flow to real backend`

---

## 8. 阶段 6：增加一条无 HTTP Mock 的黄金链路

**优先级：** P0，真实组合版本的核心证据。

### Task 6.1：新增真实集成 Playwright 项目

**Files:**

- Create: `frontend/e2e/integration/golden-path.spec.ts`
- Modify: `frontend/playwright.config.ts`
- Modify: `frontend/package.json`
- Create: `scripts/run-golden-path.sh`

**Golden path:**

```text
注册/登录
→ 浏览商品
→ AI 通过真实 Java 获取候选并调用真实 Python
→ 加购物车
→ 新建地址
→ 结算预览
→ 携带 Idempotency-Key 下单
→ Mock 支付
→ 查询支付与订单状态
→ 验证订单商品和地址快照
```

- [ ] 新增 `npm run test:e2e:integration`，只执行 `e2e/integration`。
- [ ] integration 项目以已启动的 Compose 前端地址为 `baseURL`，不启动 Vite Mock 环境。
- [ ] 黄金链路文件中禁止 `page.route`、HAR 回放和浏览器侧 API 伪造。
- [ ] 现有 `npm run test:e2e` 保留为快速 Mock UI 回归。
- [ ] 每次运行生成唯一用户名和幂等键，避免并行/重跑数据冲突。
- [ ] 不断言自然语言逐字相等；断言 Python 返回的商品引用来自 Java 候选集、结构合法且可继续交易。

### Task 6.2：在 Provider Adapter 注入确定性替身

**Files:**

- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/config_data.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/infrastructure/llm_client.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/infrastructure/vector_store.py`
- Create: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/infrastructure/deterministic_provider.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_config_data.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_llm_client.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_vector_store.py`
- Modify: `docker-compose.demo.yml`
- Modify: `.env.demo.example`

- [ ] 为集成环境增加明确的 deterministic provider 配置，作用点位于 Kimi/Jina Adapter 内部。
- [ ] 真实启动 FastAPI、LangGraph、Java HTTP client 和 SSE；只替换最外部模型/Embedding 供应商响应。
- [ ] 替身根据固定输入返回结构化、可追踪结果，不绕过 Java 候选校验。
- [ ] 普通生产 profile 禁止误启用 deterministic provider；仅 `test`/`integration` profile 可用。
- [ ] Compose 使用已有镜像和服务，不重新创建容器化方案。

### Task 6.3：增加关键断言和失败证据

- [ ] 注册用户、地址、购物车、订单真实写入 MySQL。
- [ ] 另一个用户不能使用该地址预览或下单。
- [ ] 预览金额、下单金额和订单项金额来自服务端事实。
- [ ] 相同幂等键重复提交只存在一个订单；改变地址后复用同键返回冲突。
- [ ] 订单详情地址来自快照；修改地址簿后订单详情不变。
- [ ] Mock 支付重复请求/回调只产生一次有效状态迁移。
- [ ] 失败时保存 Playwright trace、脱敏 Java/Python/Nginx 日志和 `docker compose ps`。

### 阶段 6 验收

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem
docker compose -f docker-compose.yml -f docker-compose.demo.yml config --quiet
sh scripts/run-golden-path.sh
sh scripts/run-golden-path.sh
sh scripts/run-golden-path.sh
```

**完成条件：** 从空卷启动后，黄金链路连续通过三次；浏览器 HTTP 未被拦截；Java、Python、MySQL、Redis、RabbitMQ、Elasticsearch、PostgreSQL 和前端处于真实进程边界。

**为什么现在做：** 这是唯一能证明 React、Java、Python、数据库和基础设施的契约、版本、启动顺序和运行时行为真正兼容的门禁。

**面试表达：**

> 我保留了 Mock E2E 做快速 UI 回归，但另外建立了一条不拦截浏览器 HTTP 的黄金链路。Java、Python 和数据库都是真实进程，只在 Kimi/Jina Adapter 处使用确定性替身，因此测试既稳定，又覆盖了跨服务序列化、鉴权、事务和基础设施兼容性。

**建议提交边界：**

1. `test: add real full stack shopping golden path`
2. `test: add deterministic ai provider for integration`
3. `test: capture sanitized full stack failure evidence`

---

## 9. 阶段 7：建立分层 CI 和版本关系

**优先级：** P0 发布门禁。

### Task 7.1：PR 快速检查

**Files:**

- Modify: `.github/workflows/ci.yml`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/.github/workflows/code-quality.yml`

**PR jobs:**

```text
shared-contract
backend-verify
python-quality-and-tests
frontend-test-and-build
container-config-and-build
```

- [ ] `shared-contract` 同时运行 Python 和 Java 契约测试。
- [ ] `backend-verify` 运行 Maven `verify`，包括 Checkstyle。
- [ ] Python job 运行 pytest、Ruff、compileall 和现有文档覆盖检查。
- [ ] frontend job 运行 Vitest 和 production build；Mock Playwright 可按耗时作为独立 job。
- [ ] 容器 job 只验证已有 Dockerfile 构建和 Compose 配置，不推送镜像。
- [ ] 使用官方 Maven/npm/pip 缓存、job timeout 和同分支并发取消。
- [ ] 纯文档改动使用路径过滤跳过昂贵镜像构建，但共享契约文档/Schema 改动不得跳过契约 job。

### Task 7.2：主分支、定时与手动跨服务工作流

**Files:**

- Create: `.github/workflows/cross-service.yml`
- Create: `.services/python-revision`
- Modify: `scripts/run-golden-path.sh`

- [ ] 触发条件为主分支 push、每日定时和 `workflow_dispatch`。
- [ ] 使用 `.services/python-revision` 中的 commit SHA 检出已验证兼容的 Python 版本，不跟随随机主分支。
- [ ] 升级 Python revision 的 PR 必须同时通过契约和三次黄金链路。
- [ ] 从空卷启动 Compose，执行真实黄金链路，结束时始终清理容器和卷。
- [ ] CI 失败上传脱敏日志和测试报告，禁止上传 `.env`、JWT、内部令牌、密码或模型 Key。
- [ ] 共享契约目录必须进入可版本化仓库；若继续作为独立目录，使用固定 revision/submodule/package，不依赖开发者本地未提交文件。

### 阶段 7 验收

- [ ] 一个只改 Java 的 PR 能独立看到 Java 和契约结果。
- [ ] 一个改变 Python DTO 的 PR 会被共享契约门禁捕获。
- [ ] 一个升级 Python revision 的 PR 必须通过真实黄金链路。
- [ ] 主分支黄金链路失败时，可以仅凭脱敏 artifact 区分容器启动、契约、业务或测试失败。
- [ ] 连续三次重新运行结果一致，不依赖外部模型实时可用性。

**为什么现在做：** 单仓库绿色只能证明各自正确；固定 revision 加跨服务黄金链路才能证明“这组版本”兼容，并防止下次改动把已经打通的链路悄悄破坏。

**面试表达：**

> 我把 CI 分成快速 PR 门禁和较重的跨服务门禁。PR 先按技术栈反馈，主分支和定时任务再启动完整 Compose。Java 仓库固定已验证的 Python commit SHA，升级 revision 必须重新跑契约和黄金链路，所以我们验证的是明确的组合版本，而不是两个仓库各自绿色。

**建议提交边界：**

1. `ci: split fast project quality gates`
2. `ci: pin compatible python revision`
3. `ci: run real cross service golden path`

---

## 10. 阶段 8：生产安全与运维

**优先级：** P1，上线前必须完成；可以在阶段 7 后分小批次推进。

### Task 8.1：配置与密钥 fail-closed

**Files:**

- Create: `backend/src/main/resources/application-production.properties`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/JwtProperties.java`
- Modify: `backend/src/main/resources/logback-spring.xml`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/config_data.py`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/app.py`
- Modify: `.env.demo.example`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfigTests.java`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/tests/test_config_data.py`

- [ ] 增加明确 production profile；默认 JWT 密钥、内部服务令牌、数据库密码、支付回调密钥为空或示例值时启动失败。
- [ ] `.env.example` 只记录变量名和非敏感本地默认值；真实值由 Secret Store/部署平台注入。
- [ ] Java、Python、Nginx 日志统一脱敏 Authorization、Cookie、JWT、手机号、地址、密码和模型 Key。
- [ ] 错误响应不返回栈、SQL、磁盘路径、供应商原始错误或内部 URL。

### Task 8.2：最小权限与网络边界

**Files:**

- Modify: `docker-compose.yml`
- Modify: `docker-compose.demo.yml`
- Modify: `frontend/nginx.conf`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java`
- Modify: `backend/src/main/resources/application-production.properties`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfigTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/observability/ObservabilityEndpointsTests.java`

- [ ] Java、Python、迁移/运维任务使用独立数据库用户；应用用户不拥有建库和全局管理权限。
- [ ] MySQL、Redis、RabbitMQ、PostgreSQL、Actuator 和 Python internal API 不暴露到公网。
- [ ] Actuator 只公开必要健康端点；metrics 和管理端点走内网认证。
- [ ] 应用容器使用非 root 用户、只读文件系统可行部分、资源 limit、健康检查和合理重启策略。
- [ ] 安全配置测试验证未认证用户不能访问地址、结算、订单、收藏和管理端点。

### Task 8.3：Refresh Token Cookie 化

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/api/AuthController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/service/AuthService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/AuthTokenResponse.java`
- Delete after migration: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/RefreshTokenRequest.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/AuthControllerTests.java`
- Modify: `frontend/src/features/auth/useAuthSession.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/client.test.ts`

- [ ] Refresh Token 从 JSON/本地存储迁移到 `HttpOnly; Secure; SameSite` Cookie。
- [ ] Access Token 保持短时有效；刷新端点增加 CSRF/Origin 策略并验证跨站请求。
- [ ] 登出撤销服务端 Refresh Token 并清除 Cookie；多设备会话继续可审计。
- [ ] 开发环境和生产环境 Cookie 配置分离，生产环境禁止降级为非 Secure。

### Task 8.4：可观测性与告警

**Files:**

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`
- Modify: `observability/prometheus.yml`
- Modify: `observability/grafana/dashboards/java-commerce-core.json`
- Modify: `observability/grafana/dashboards/ai-shopping-assistant.json`
- Create: `observability/grafana/provisioning/datasources/prometheus.yml`
- Create: `observability/grafana/provisioning/dashboards/dashboards.yml`
- Create: `observability/alert-rules.yml`
- Modify: `docker-compose.yml`
- Modify: `observability/README.md`
- Modify: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/app.py`

- [ ] Prometheus 真正抓取 Java 和 Python 指标，Grafana dashboard 使用可复现 provisioning。
- [ ] 最小指标覆盖请求延迟/错误率、Java→Python 调用、fallback、推荐候选数、结算失败原因、下单成功率、库存冲突、幂等命中/冲突、MQ 重试/DLQ。
- [ ] label 不包含 userId、query、orderNo 或其他高基数字段。
- [ ] 建立四类基础告警：服务不可用、错误率升高、黄金链路失败、MQ/DLQ 堆积。
- [ ] trace/correlation ID 跨 Nginx、Java、Python、MQ 传播，日志可按一次用户请求关联。

### Task 8.5：恢复、扫描与运维演练

**Files:**

- Create: `scripts/backup-databases.sh`
- Create: `scripts/restore-databases.sh`
- Create: `docs/operations/backup-restore-runbook.md`
- Create: `docs/operations/failure-drills.md`
- Create: `.github/workflows/security.yml`
- Modify: `.github/workflows/cross-service.yml`
- Modify: `README.md`

- [ ] 对 MySQL 和 LangGraph PostgreSQL 执行一次真实备份与恢复演练，记录 RPO/RTO 实测值。
- [ ] 演练订单创建中断、Python 不可用、Redis 不可用、RabbitMQ 消费失败和 DLQ 重放。
- [ ] CI 增加依赖漏洞、镜像漏洞、密钥泄露和许可证扫描；高危问题阻断发布。
- [ ] 建立迁移回滚/前滚说明、发布检查表和脱敏日志保留策略。
- [ ] 验证健康检查只表达进程与必要依赖状态，不把外部模型短暂失败错误地变成整个商城不可用。

### 阶段 8 验收

- [ ] 使用默认/示例密钥启动 production profile 会明确失败。
- [ ] 外部网络无法直连内部数据库、MQ、Actuator 管理端点和 Python internal API。
- [ ] Refresh Token 不可被前端 JavaScript 读取。
- [ ] Grafana 可以展示 Java/Python/交易/MQ 的真实指标，告警可通过故障演练触发。
- [ ] 从备份恢复到新数据库后，核心订单和地址快照可查询，记录实际恢复时间。
- [ ] 发布流水线无高危未处理漏洞或泄露密钥。

**为什么现在做：** 黄金链路证明“能运行”，生产安全与运维证明“暴露给真实用户后仍可控制、观察和恢复”。两者不能互相替代。

**面试表达：**

> 我把生产化拆成 fail-closed 配置、最小权限、Cookie 化 Refresh Token、可观测性和恢复演练。监控不是只搭 Prometheus/Grafana，而是让 Java、Python、交易和 MQ 指标真正可抓取，并通过故障演练验证告警和恢复时间。

**建议提交边界：**

1. `security: fail closed production secrets`
2. `security: isolate internal services and actuator`
3. `security: move refresh token to secure cookie`
4. `ops: wire java python metrics and dashboards`
5. `ops: add backup restore and security gates`

---

## 11. P2：真实支付单独立项

真实支付不和前八阶段并行实施。只有满足以下入口条件后才创建独立设计与实施计划：

- [ ] 地址、结算、订单快照和幂等事务稳定。
- [ ] Mock 支付黄金链路连续稳定。
- [ ] production secret、日志脱敏、指标、告警和恢复演练完成。
- [ ] 已选定具体支付渠道、商户环境和合规要求。

独立支付计划至少覆盖：

- 官方 SDK 和官方服务端验签。
- 金额、商户号、订单号和支付状态的服务端核对。
- 重复/乱序回调幂等。
- 主动查询补偿与人工补单入口。
- 退款、部分退款、退款回调和对账。
- 密钥轮换、证书过期告警和沙箱/生产隔离。
- 财务对账差异的审计记录。

**面试表达：**

> 当前项目实现的是可重复验证的 Mock 支付闭环，我不会把它包装成生产支付。真实支付会使用官方 SDK、官方验签、重复回调幂等、退款、主动查询补偿和对账，并且必须建立在可信订单和生产运维门禁之后。

---

## 12. 接口总表与面试讲解主线

### 12.1 本阶段直接涉及的接口

| 业务 | 接口 | 关键边界 |
| --- | --- | --- |
| 注册 | `POST /api/auth/register` | 真实写入 MySQL，黄金链路起点 |
| 登录 | `POST /api/auth/login` | 获取访问身份，后续 userId 不由前端提交 |
| 商品浏览 | `GET /api/products` | MySQL/ES 搜索，但价格和库存最终回到 Java 事实 |
| AI 同步 | `POST /api/assistant/chat` → Python `/chat` | Java 组装候选，Python 只排序和解释 |
| AI 流式 | `POST /api/assistant/chat/stream` → Python `/chat/stream` | SSE 与同步响应共享结构契约 |
| 购物车 | `/api/cart/items` | 用户选择进入服务端购物车，数量成为结算事实 |
| 地址 | `/api/addresses` | JWT 所有权、默认地址事务、越权不泄露 |
| 结算 | `POST /api/checkout/preview` | 只接收 skuIds/addressId，服务端计算金额 |
| 下单 | `POST /api/orders` | addressId、Idempotency-Key、正式重新计算 |
| 收藏 | `/api/favorites` | 前后端路径、参数形式和列表能力统一 |
| Mock 支付 | `POST /api/payments`，`channel=MOCK` | 只验证订单/支付状态机，不代表真实支付 |
| 支付查询 | `GET /api/payments/{paymentNo}` | 当前用户所有权和状态查询 |
| 订单查询 | `/api/orders` | 验证订单状态、商品快照和地址快照 |

### 12.2 两分钟面试说辞

> 这个项目不是简单把 React、Java、Python 和几个中间件放在一起。我先划分事实边界：用户、商品、价格、库存、地址、订单和支付由 Java/MySQL 负责，Python 只处理意图、RAG、排序解释和生成。然后我先用共享字段清单建立 Java DTO 与 Pydantic 的跨语言契约门禁，并让测试通过 Adapter 使用确定性模型替身。
>
> 交易链路上，我先做用户地址所有权，再把购物车、当前价格、商品状态、库存和金额公式封装进 CheckoutCalculator。预览只做时点计算，正式下单重新计算；前端不能提交金额。订单保存下单时地址快照，并把商品与地址共同纳入幂等指纹，订单、订单项、快照、库存、购物车和幂等结果处于同一事务。
>
> 测试方面，我保留 Mock E2E 做快速回归，同时增加不拦截浏览器 HTTP 的真实黄金链路，真实启动 React、Java、Python 和数据库，只在最外部模型 Adapter 处使用确定性替身。最后在 CI 固定兼容的 Python revision，PR 跑快速门禁，主分支和定时任务跑完整 Compose 和黄金链路。这样我能证明的不只是各仓库单独通过，而是一组明确版本可以真实组合运行。

### 12.3 追问时的四个核心回答

**为什么 Java 是事实源？**

> AI 输出具有概率性，交易事实必须可审计和可重放。Python 可以选择、排序和解释 Java 给出的候选，但价格、库存、地址归属和订单状态必须在 Java 重新校验。

**为什么预览和下单都计算一次？**

> 预览到下单之间价格和库存可能变化，所以预览不能成为承诺。二者共享同一 Calculator 避免规则漂移，但下单必须重新读取事实并在事务中执行。

**为什么既有 Mock E2E 又有真实 E2E？**

> Mock E2E 快、定位 UI 问题容易；真实 E2E 才能覆盖路由、序列化、鉴权、事务、数据库和服务启动。两类测试解决的问题不同，不能互相替代。

**为什么真实支付最后做？**

> 真实支付会引入资金、验签、退款、对账和人工补偿风险。订单事实、幂等、回调状态机、日志脱敏、告警和恢复没有稳定之前，提前接渠道只会放大风险。

---

## 13. 实际执行检查表

### Gate 1：现在立即开始

- [x] Task 1.1 契约测试固定预期。
- [x] Task 1.2 `rejected_reasons` 正式进入 Python 响应。
- [x] Task 1.3 完成 `demand-intent-v3`、`IntentConstraint`、`SubjectMeasurements`。
- [x] Task 1.4 发布三份 RAG rebuild JSON Schema。
- [x] Task 1.5 pytest 与 Fake Embedding Adapter。
- [x] 阶段 1 全部验收命令通过。

### Gate 2：可信交易纵切面

- [ ] 地址 CRUD、所有权和默认地址并发。
- [ ] CheckoutCalculator 与预览接口。
- [ ] 订单地址快照、重新计算、事务和幂等指纹。
- [ ] Java 全量 `verify`、MySQL 并发测试、前端单测和 build 通过。

### Gate 3：消除假闭环

- [ ] 收藏接口统一。
- [ ] 未实现确认收货入口移除。
- [ ] 地址、预览、下单切换真实后端。
- [ ] 真实黄金链路连续通过三次。

### Gate 4：固化为发布能力

- [ ] PR 分层 CI。
- [ ] Python revision 固定。
- [ ] 主分支/定时跨服务工作流。
- [ ] 脱敏失败证据。

### Gate 5：上线门禁

- [ ] production fail-closed。
- [ ] 网络、数据库、Actuator 最小权限。
- [ ] Refresh Token Cookie 化。
- [ ] 指标、Dashboard、告警和 trace 生效。
- [ ] 备份恢复和故障演练完成。
- [ ] 漏洞、镜像和密钥扫描通过。

---

## 14. 第一阶段开始前的基线记录

开始编码前，在阶段 1 的首个提交或 PR 描述中记录以下基线，避免修复过程中丢失因果关系：

- Python 全量测试当前失败项及其分类：缺模型、字段不一致、缺 Schema、真实 Embedding 被意外调用。
- Java 共享契约、同步客户端、SSE parser 的当前结果。
- 前端当前使用 Mock route 的 E2E 数量，以及真实地址/结算/收藏接口差异。
- 已存在的 Dockerfile、`docker-compose.yml`、`docker-compose.demo.yml` 和 Compose config 结果。
- Python 仓库已有的本地 IDE 改动不纳入本阶段提交。

基线记录的目的不是保留红色，而是让每一次修复都能对应一个可解释的失败，并让面试时可以清楚说明“问题是什么、如何建立门禁、修复后证明了什么”。
