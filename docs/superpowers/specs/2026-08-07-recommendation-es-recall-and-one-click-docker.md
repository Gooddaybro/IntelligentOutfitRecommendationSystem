# 推荐 ES 召回与一键 Docker 启动开发设计

日期：2026-08-07
状态：开发中，按 `docs/superpowers/plans/2026-08-07-recommendation-es-recall-and-docker-demo.md` 执行
适用项目：`IntelligentOutfitRecommendationSystem` + `AI-Clothing-Shopping-Assistant-System` + `outfit-project-contract`

## 1. 结论

当前项目已经完成了一部分 Elasticsearch 能力，但还没有把它接入“聊天推荐候选召回”。

已经有：

- 商品搜索 ES 副本：`product_current` 别名、商品索引 Mapping、ES 查询网关、MySQL 降级网关。
- 从 MySQL 全量重建 ES 商品索引的内部接口：`POST /internal/search/products/rebuild`。
- 推荐候选事实边界：Java 从 MySQL/Redis 拿候选，再交给 Python 排序和解释。

还没有：

- 推荐候选召回使用 ES。现在 `RecommendationCandidateQueryService` 仍只按 MySQL 硬过滤查 SKU 候选。
- 一键启动完整系统。当前 Compose 只启动 MySQL、Redis、RabbitMQ、LangGraph PostgreSQL、Elasticsearch、Kibana 等基础设施；Java 后端、Python AI、React 前端仍需要手动跑。
- 一键启动后的搜索索引初始化流程。虽然已有重建接口，但还没有被脚本或容器启动流程串起来。

推荐做法：

1. 推荐 ES 召回只接在 Java 的候选查询边界，复用现有 `ProductSearchGateway` / `product_current` / MySQL 实时补齐，不让 Python 直接查 ES。
2. 一键部署做成“本地演示/面试级 Docker Compose”，而不是上来做 Kubernetes、云数据库、CI/CD 全套生产部署。
3. 先加功能开关和可观测指标，默认保持旧逻辑；确认效果后再让聊天推荐走 ES 主召回。

## 2. 排查到的现状

### 2.1 推荐链路现状

聊天推荐当前链路：

```mermaid
flowchart LR
    U["用户聊天请求"] --> J1["Java AssistantContextService"]
    J1 --> J2["构造 RecommendationCandidateQuery"]
    J2 --> J3["RecommendationCandidateQueryService"]
    J3 --> DB["MySQL 查询 SKU 候选快照"]
    J3 --> R["Redis 缓存静态候选快照"]
    J3 --> DB2["MySQL 补实时价格/库存"]
    DB2 --> P["Python 推荐排序/解释"]
    P --> J4["Java 校验 product refs"]
    J4 --> U
```

关键文件：

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
  - 根据用户画像、历史、已解析硬约束构造 `RecommendationCandidateQuery`。
  - 调用 `recommendationCandidateQueryService.findCandidates(query)`。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/RecommendationCandidateQueryService.java`
  - 当前只查 MySQL 的 `findRecommendationCandidateSnapshots`。
  - Redis 只缓存静态快照。
  - 价格、库存每次从 MySQL `findRecommendationCandidateLiveFacts` 补齐。
- `backend/src/main/resources/mapper/product/ProductMapper.xml`
  - `findRecommendationCandidateSnapshots` 已经支持类目、风格、季节、材质、版型、性别等硬过滤。
- `outfit-project-contract/contracts/java-python-chat/v1.fields.json`
  - Python 接收 Java 提供的 `candidates`。
  - Python 不应该返回无法追溯到 Java 候选的商品。

判断：推荐召回应放在 `RecommendationCandidateQueryService`，而不是 Python 层、前端层、或另起一套推荐搜索服务。

### 2.2 商品搜索 ES 现状

当前 ES 搜索链路已经存在：

```mermaid
flowchart LR
    API["/api/products 或 /internal/products/search"] --> S["ProductSearchService"]
    S --> ES["ElasticsearchProductSearchGateway"]
    ES --> IDs["有序 SPU ID"]
    S --> DB["MySQL 按 SPU ID 补当前商品事实"]
    S --> OUT["返回商品摘要"]
    ES -.不可用.-> FB["MySqlProductSearchGateway 降级"]
    FB --> DB
```

已有能力：

- `ProductSearchGateway`：搜索抽象，返回有序 SPU ID。
- `ElasticsearchProductSearchGateway`：使用 ES `multi_match` 做多字段召回，字段包含 `name.smartcn^5`、`styles.search^3`、`category.search^2`、`scenes.search^2`、`materials.search^1.5`、`description.smartcn`。
- `ProductSearchService`：ES 查 ID，MySQL 补事实；ES 连接失败、404、5xx 时降级 MySQL。
- `InternalProductSearchIndexController`：`POST /internal/search/products/rebuild` 从 MySQL 全量重建索引并切换别名。

判断：推荐召回不要新建 ES 客户端和新索引，第一阶段应复用这套搜索副本。

### 2.3 Docker/一键部署现状

当前 `docker-compose.yml` 服务：

- `mysql`
- `redis`
- `rabbitmq`
- `langgraph-postgres`
- `elasticsearch`
- `kibana`

没有包含：

- Java 后端 Web 服务容器。
- Java Worker 容器。
- Python AI FastAPI 容器。
- React 前端生产容器。
- Nginx/API 反向代理。
- 一键启动后的索引重建步骤。
- Prometheus/Grafana 服务；但 `observability/README.md` 已经写了 `prometheus grafana` 启动说明，Compose 里暂未定义。

根目录 `README.md` 仍要求手动执行：

- Python：创建 venv、安装依赖、启动 `uvicorn`。
- Java：进入 `backend` 后执行 Maven 启动。
- 前端：进入 `frontend` 后执行 `npm ci`、`npm run dev`。

判断：现在不是“别人拉下来后一条命令完整运行”的状态；最多只能算“一键启动部分依赖”。

## 3. 与生产级/面试级项目的差异

这里按生产实践做校准，不照搬模板。

| 能力项 | 生产级/面试级期望 | 本项目现状 | 差距 |
|---|---|---|---|
| 搜索召回 | 搜索引擎负责文本相关性，数据库负责事实一致性 | 商品搜索已做到，推荐候选未接入 | 推荐召回仍是 MySQL 硬过滤 |
| 推荐链路边界 | 召回、过滤、排序、解释职责清晰 | Java 召回 + Python 排序边界基本清晰 | ES 召回未纳入 Java 候选边界 |
| 降级策略 | 搜索副本不可用不阻断核心交易/推荐 | 商品搜索已有降级 | 推荐 ES 召回还没有降级逻辑 |
| 索引生命周期 | 全量重建、别名切换、失败不污染当前索引 | 已有重建接口和别名切换 | 未接一键启动/自动初始化 |
| 增量同步 | 商品变更后异步更新 ES | 已有 Product Search Sync 配置和 RabbitMQ 基础 | 一键部署未启用 Worker；推荐侧未验证 |
| 可观测性 | 有核心指标、健康检查、启动顺序 | Actuator、Prometheus endpoint、RabbitMQ healthcheck 已有 | Compose 缺完整观测服务；推荐召回指标缺失 |
| 可复现环境 | clone 后少量命令启动全栈 | 依赖可部分启动，应用需手动 | 缺 app Dockerfile、统一 env、启动脚本 |
| 测试验收 | 单元、Mapper、契约、端到端场景 | 项目已有测试体系 | 新召回与 Docker 方案需补验收 |

参考资料：

- Elastic 官方 `multi_match` 文档说明，一个查询可以跨多个字段，并支持字段权重，例如 `field^3`。本项目现有商品搜索已经使用这一模式，不需要重写查询轮子。
- Docker Compose 官方文档支持 `healthcheck` 和 `depends_on` 健康条件，适合把 MySQL、Redis、RabbitMQ、ES、Python、Java、前端按就绪顺序串起来。
- Spring Boot 官方容器镜像文档支持分层 Jar，适合减少 Java 镜像重复构建成本。

## 4. 推荐 ES 召回设计

### 4.1 目标

在聊天推荐里，把“自然语言需求文本”用于 ES 召回，先拿有序 SPU ID，再回 MySQL 展开成 SKU 级推荐候选，最后仍交给 Python 排序和解释。

目标效果：

- 用户说“通勤、显瘦、适合秋天的半裙”，召回不只依赖类目/季节/材质等硬过滤，也能利用商品名、描述、风格、场景、材质字段的文本相关性。
- 推荐链路仍保证价格、库存、上下架状态来自 MySQL。
- ES 不可用时不影响旧推荐链路。

### 4.2 不做什么

第一阶段不做：

- 不让 Python 直接查询 ES。
- 不新建 SKU 级推荐索引。
- 不引入向量召回。
- 不引入 IK 和同义词词库调整，除非后续评测证明 SmartCN 召回不足。
- 不改变 Java-Python `candidates` 合同。
- 不把预算、库存、价格放到 ES 里作为事实判断。

原因：当前项目已有 SPU 级商品搜索副本，最短路径是复用它；价格/库存是交易事实，必须继续以 MySQL 为准。

### 4.3 建议接入点

推荐接入点：

```text
AssistantContextService
  -> RecommendationCandidateQueryService.findCandidates(query)
      -> ES 召回有序 SPU ID
      -> MySQL 按 SPU ID + 硬过滤展开 SKU 候选快照
      -> Redis 缓存静态候选快照
      -> MySQL 补实时价格/库存
      -> 返回 Java candidates 给 Python
```

需要改动：

1. `RecommendationCandidateQuery`
   - 增加可选 `recallText`。
   - 含义：自然语言召回文本，只用于 ES 相关性召回，不作为硬过滤。
   - 向后兼容：为空时保持当前 MySQL 逻辑。

2. `AssistantContextService`
   - 从用户本轮请求/有效需求中构造 `recallText`。
   - 推荐优先使用“用户原始消息 + 已确认硬需求的轻量拼接”，避免只用结构化字段导致语义变窄。

3. `RecommendationCandidateQueryService`
   - 注入现有搜索网关或一个很薄的推荐召回网关。
   - 当 `app.recommendation.es-recall.enabled=true` 且 `recallText` 非空时：
     - 调用 ES 搜索得到有序 SPU ID。
     - 用 MySQL 查询把这些 SPU 展开为 SKU 候选快照。
     - 继续用原来的实时事实补齐和预算过滤。
   - 当功能关闭、`recallText` 为空、或 ES 不可用时：
     - 保持当前 MySQL 逻辑。

4. `ProductMapper`
   - 新增 `findRecommendationCandidateSnapshotsBySpuIds(query, spuIds)`。
   - SQL 复用现有 `findRecommendationCandidateSnapshots` 的字段和硬过滤。
   - Java 内存中按 ES 返回的 SPU 顺序重排，因为 SQL `IN` 不保证顺序。

5. Redis 缓存 key
   - 当前候选缓存 key 只包含类目、风格、季节、材质、版型、性别。
   - 接入 ES 后必须把 `recallText` 的标准化摘要加入 key，否则不同文本会命中同一份候选缓存。
   - `budgetMax` 仍可不进 key，因为预算是在实时补齐后过滤，不进入静态快照缓存。

### 4.4 推荐召回流程

```mermaid
flowchart TD
    A["AssistantContextService 构造 query + recallText"] --> B{"ES 推荐召回开启且 recallText 非空？"}
    B -- "否" --> C["现有 MySQL 候选查询"]
    B -- "是" --> D["ES multi_match 召回有序 SPU ID"]
    D -- "ES 不可用" --> C
    D -- "命中 ID" --> E["MySQL 按 SPU ID + 硬过滤展开 SKU 快照"]
    E --> F["Redis 缓存静态快照，key 包含 recallText hash"]
    C --> F
    F --> G["MySQL 补实时价格/库存"]
    G --> H["预算/库存过滤"]
    H --> I["Python 只在候选内排序和解释"]
```

### 4.5 ES 查询策略

第一阶段复用现有字段：

- `name.smartcn^5`
- `styles.search^3`
- `category.search^2`
- `scenes.search^2`
- `materials.search^1.5`
- `description.smartcn`

硬过滤保留在 MySQL：

- 性别：当前 ES 文档没有稳定 gender 字段。
- 版型：MySQL 使用 `fit` code，ES 里是 `fitType` 名称，语义不完全一致。
- 预算、库存、价格：必须来自 MySQL 实时事实。
- SKU 属性：ES 是 SPU 文档，不应直接判断 SKU 可售性。

ES 可作为 filter 的字段：

- `status=on_sale`
- `category`，如果 Java 已经解析出明确类目。

### 4.6 空结果与降级策略

建议用两阶段开关：

1. `app.recommendation.es-recall.shadow-enabled`
   - 影子模式。
   - 实际仍返回 MySQL 候选。
   - 后台记录 ES 命中数量、MySQL 候选数量、交集比例。

2. `app.recommendation.es-recall.enabled`
   - 主链路模式。
   - ES 可用且有 `recallText` 时使用 ES 命中 ID 限定候选。
   - ES 不可用时回退 MySQL。
   - ES 返回空结果时返回空候选，并记录指标；如果担心体验，可临时加 `empty-result-fallback-enabled=true`，但默认不建议长期打开，因为会掩盖召回质量问题。

推荐开发顺序：先实现 `enabled`，同时保留默认关闭；如时间允许再加 shadow 指标。这样不影响当前稳定链路。

### 4.7 需要新增的测试

Java 后端：

- `RecommendationCandidateQueryServiceTests`
  - ES 开关关闭时完全走旧逻辑。
  - ES 开启且 `recallText` 非空时先取 ES SPU ID。
  - ES 不可用时回退 MySQL。
  - ES 空结果不误查全量 MySQL。
  - 返回候选保持 ES SPU 顺序，再按 SKU 规则稳定排序。
  - Redis key 包含 `recallText` 摘要。

- `ProductCatalogMapperTests`
  - `findRecommendationCandidateSnapshotsBySpuIds` 复用现有硬过滤。
  - 空 SPU ID 列表不生成非法 SQL。
  - 同一 SPU 下多 SKU 可以正确展开。

- `ElasticsearchProductSearchGatewayTests`
  - 现有商品搜索测试尽量复用。
  - 如需要只补推荐召回的 criteria/limit 行为。

契约：

- Java-Python chat 合同不应变化。
- 只要 Java 传给 Python 的 `candidates` 字段不变，Python 不需要改合同。

### 4.8 验收标准

功能验收：

- 未开启 `APP_RECOMMENDATION_ES_RECALL_ENABLED` 时，推荐结果与当前逻辑一致。
- 开启后，聊天推荐候选先被 ES 自然语言召回限定，再由 MySQL 补齐实时价格/库存。
- 停掉 ES 后，聊天推荐自动回退 MySQL，不报 500。
- Python 返回的所有商品引用仍能在 Java candidates 中找到。

质量验收：

- 至少准备 8 条中文查询用例：
  - `通勤半裙`
  - `秋天显瘦针织`
  - `约会连衣裙`
  - `运动休闲外套`
  - `职场衬衫`
  - `梨形身材显瘦`
  - `夏天透气`
  - `黑色简约`
- 每条记录 ES 命中数量、最终候选数量、Python 推荐前 3 个商品。
- 如果 ES 召回明显差，再进入分词/同义词优化；不要提前引入词库工程。

## 5. 一键 Docker 启动设计

### 5.1 目标

让别人 clone 项目后，可以用最少步骤启动完整演示环境：

```bash
cp .env.demo.example .env
sh scripts/start-demo.sh
```

启动后应能访问：

- 前端商城：`http://localhost:3000`
- Java API：`http://localhost:8080`
- Python AI：`http://localhost:8000`
- Elasticsearch：`http://localhost:9200`
- Kibana：`http://localhost:5601`
- RabbitMQ 管理台：`http://localhost:15672`

### 5.2 推荐容器组成

复用现有基础设施 Compose，新增应用服务：

```mermaid
flowchart TD
    FE["frontend: Nginx + Vite build"] --> BE["backend-web: Spring Boot web"]
    BE --> PY["python-ai: FastAPI"]
    BE --> MYSQL["mysql"]
    BE --> REDIS["redis"]
    BE --> MQ["rabbitmq"]
    BE --> ES["elasticsearch"]
    BW["backend-worker: Spring Boot worker profile"] --> MQ
    BW --> ES
    BW --> MYSQL
    BW --> PY
    IDX["search-index-init: 调用 rebuild 接口后退出"] --> BE
    IDX --> ES
```

新增服务建议：

| 服务 | 作用 | 说明 |
|---|---|---|
| `backend-web` | Java Web/API | 使用同一个后端镜像，`SPRING_PROFILES_ACTIVE=web` |
| `backend-worker` | Java 异步任务/同步消费者 | 使用同一个后端镜像，打开 listener 开关 |
| `python-ai` | FastAPI AI 服务 | 暴露 `/health`，内部 token 与 Java 一致 |
| `frontend` | 生产前端 | Vite build 后由 Nginx 提供静态资源，并代理 `/api` 到 Java |
| `search-index-init` | 一次性初始化 ES 商品索引 | 等 Java ready 后调用 `/internal/search/products/rebuild`，成功后退出 |

### 5.3 需要新增/修改的文件

建议新增：

- 根目录或 Java 项目内：`docker-compose.demo.yml`
- Java 后端：`backend/Dockerfile`
- Python AI：`../AI-Clothing-Shopping-Assistant-System/Dockerfile`
- 前端：`frontend/Dockerfile`
- 前端 Nginx：`frontend/nginx.conf`
- 根目录：`.env.demo.example`
- 根目录：`scripts/start-demo.sh`
- 根目录：`scripts/stop-demo.sh`

是否合并到现有 `docker-compose.yml` 有两个选择：

1. 推荐：保留现有 `docker-compose.yml` 作为依赖环境，新增 `docker-compose.demo.yml` 专管全栈演示。
   - 优点：不破坏当前开发者本地流程。
   - 缺点：命令需要 `-f` 指定两个 Compose 文件，脚本可以隐藏复杂度。

2. 备选：直接扩展现有 `docker-compose.yml`，用 profile 控制 `app`、`observability`。
   - 优点：文件少。
   - 缺点：当前文件会变大，学习者更难区分依赖和完整系统。

推荐选 1。

### 5.4 容器环境变量映射

容器内不能再使用 `localhost` 访问其它服务，必须换成 Compose 服务名。

Java 后端：

```text
SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/intelligent_outfit?...
SPRING_DATA_REDIS_HOST=redis
SPRING_RABBITMQ_HOST=rabbitmq
APP_AI_PYTHON_BASE_URL=http://python-ai:8000
APP_ELASTICSEARCH_ENABLED=true
APP_ELASTICSEARCH_URIS=http://elasticsearch:9200
APP_INTERNAL_API_TOKEN=${APP_INTERNAL_API_TOKEN}
APP_AI_PYTHON_INTERNAL_TOKEN=${APP_INTERNAL_API_TOKEN}
```

Python AI：

```text
APP_INTERNAL_API_TOKEN=${APP_INTERNAL_API_TOKEN}
AI_RUNTIME_ENV=development
LANGGRAPH_CHECKPOINTER_BACKEND=postgres
LANGGRAPH_CHECKPOINTER_DSN=postgresql://langgraph:...@langgraph-postgres:5432/langgraph
```

前端：

```text
VITE_API_BASE_URL=
```

前端生产容器建议通过 Nginx 把 `/api` 代理到 `backend-web:8080`，这样浏览器仍请求同域 API，避免额外 CORS 复杂度。

### 5.5 启动顺序

使用 Compose healthcheck：

1. MySQL、Redis、RabbitMQ、LangGraph PostgreSQL、Elasticsearch 先健康。
2. Python AI 启动并通过 `/health`。
3. Java backend-web 启动并通过 `/actuator/health/readiness`。
4. `search-index-init` 调用 Java 内部 rebuild 接口并退出成功。
5. Frontend 对外暴露。
6. backend-worker 启动，用于 RabbitMQ/RAG/ES 增量任务。

说明：

- Docker Compose 的 `depends_on.condition: service_healthy` 可以等待依赖健康后再启动下游服务。
- 对一次性任务，可以使用 `service_completed_successfully` 等待初始化容器成功结束。

### 5.6 模型密钥策略

一键启动不能提交真实模型密钥。

建议：

- `.env.demo.example` 只放占位符。
- 没有模型 key 时，核心商城、搜索、候选召回应可运行。
- 需要完整 AI 对话效果时，用户自行在 `.env` 填入模型 provider key。
- Java/Python 内部调用 token 默认给 demo 值，但文档注明不能用于生产。

### 5.7 一键部署验收标准

从干净环境验证：

```bash
git clone <repo>
cd <repo>
cp .env.demo.example .env
sh scripts/start-demo.sh
```

验收：

- `docker compose ps` 显示基础设施、Java、Python、前端服务健康。
- `curl http://localhost:8080/actuator/health/readiness` 返回可用。
- `curl http://localhost:8000/health` 返回可用。
- `curl http://localhost:9200/product_current/_count` 返回商品文档数量。
- 浏览器打开 `http://localhost:3000` 可以进入商城。
- 商品搜索中文关键词能返回结果。
- 聊天推荐在 ES 开启时能返回候选；停掉 ES 后仍能降级返回。

## 6. 推荐实施顺序

### 阶段 1：推荐 ES 召回最小闭环

目标：不影响旧逻辑，先把 ES 召回能力安全接入。

改动：

1. 给 `RecommendationCandidateQuery` 增加 `recallText`。
2. `AssistantContextService` 填充 `recallText`。
3. `RecommendationCandidateQueryService` 增加 ES 召回分支和降级。
4. `ProductMapper` 增加按 SPU ID 查推荐 SKU 快照的方法。
5. Redis key 加入 `recallText` hash。
6. 增加单元测试、Mapper 测试、降级测试。

验收后再考虑开启默认值。

### 阶段 2：召回质量校准

目标：用真实中文查询确认 ES 的价值。

改动：

1. 增加一份小型查询评测文档。
2. 记录每个查询的 ES 命中、最终候选、Python 推荐。
3. 只在证据表明需要时，再考虑 SmartCN 词库、同义词、字段权重微调。

### 阶段 3：一键 Docker 演示环境

目标：别人 clone 后可以完整跑起来。

改动：

1. 后端 Dockerfile。
2. Python Dockerfile。
3. 前端 Dockerfile + Nginx。
4. `docker-compose.demo.yml`。
5. `.env.demo.example`。
6. `scripts/start-demo.sh` 和 `scripts/stop-demo.sh`。
7. 把 ES rebuild 接入启动流程。

### 阶段 4：观测与文档收口

目标：面试时能讲清楚、也能演示。

改动：

1. 补推荐召回指标。
2. 修正 `observability/README.md` 与 Compose 的不一致。
3. 更新总 README 的一键启动说明。
4. 增加故障演示步骤：停 ES、看 MySQL 降级；重启 ES、看恢复。

## 7. 风险与取舍

| 风险 | 影响 | 处理 |
|---|---|---|
| ES 分词对中文服饰词不稳定 | 召回为空或排序差 | 先做评测，后调词库/同义词 |
| ES 只到 SPU，推荐需要 SKU | 候选粒度不一致 | MySQL 按 SPU 展开 SKU，事实仍来自 MySQL |
| 缓存污染 | 不同文本共享同一候选 | cache key 加 `recallText` hash |
| 一键启动太重 | 新人机器资源不够 | ES/Kibana/observability 用 profile，默认 demo 控制内存 |
| 模型 key 缺失 | AI 对话效果不完整 | demo 可启动，完整 AI 效果要求用户填 `.env` |
| Docker 内 localhost 错误 | 容器互相连不上 | 全部改 Compose 服务名 |

## 8. 需要确认的决策

请确认以下选项后再开发：

1. 推荐 ES 召回开关默认值
   - 建议：默认关闭，开发/演示通过环境变量开启。

2. ES 空结果策略
   - 建议：ES 可用但返回空时返回空候选，并记录指标；只对 ES 不可用做 MySQL 降级。
   - 如果你更重视演示稳定性，可以先打开“空结果也回退 MySQL”的临时开关。

3. 一键部署范围
   - 建议：先做本地 demo 级 Compose，不做云部署/K8s。

4. 是否同时补 Prometheus/Grafana
   - 建议：本次先修 Compose/README 不一致；真正 dashboard 放后续阶段。

## 9. 最终推荐

我建议按这个顺序执行：

1. 先做“推荐 ES 召回最小闭环”，因为它直接补上你截图里“ES 在推荐系统中的应用”缺口。
2. 再做“一键 Docker demo”，因为它是面试演示和项目交付体验的明显短板。
3. 最后补召回质量评测与观测文档，让功能可解释、可演示、可排障。

这条路线复用当前已有的 ES 商品搜索、副本索引、重建接口、MySQL 实时补齐、Python 排序合同；需要新增的只是推荐召回的连接层和 Docker 编排，不重复造轮子。
