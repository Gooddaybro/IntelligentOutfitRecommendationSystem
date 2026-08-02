# 商品搜索 P4 可观测性设计

## 1. 背景

P1 到 P3 已经完成商品搜索的 Elasticsearch 接入、索引生命周期、Outbox + RabbitMQ 增量同步，以及搜索缓存版本化。当前链路已经能运行，但问题发生时主要依赖日志和人工排查：

```text
用户搜索
  → Elasticsearch / MySQL 降级
  → MySQL 补齐商品事实

商品变更
  → product_search_outbox
  → RabbitMQ 重试队列 / DLQ
  → Product Search Worker
  → Elasticsearch

全量重建
  → Bulk 写入
  → 文档数量校验
  → 别名切换
```

P4 的目标是给这些关键节点补上低基数指标，让本地 Prometheus 和后续 Grafana / 告警可以回答：搜索是否变慢、是否频繁降级、同步是否反复重试或进入 DLQ、重建是否耗时异常、Bulk 是否失败、MySQL 快照数量与 ES 索引数量是否出现差异。

## 2. 目标与非目标

### 2.1 目标

- 记录 Elasticsearch 商品搜索耗时和结果状态。
- 记录商品搜索从 Elasticsearch 降级到 MySQL 的次数。
- 记录商品搜索同步 Worker 的成功、重复、重试和 DLQ。
- 记录全量重建耗时和成功 / 失败结果。
- 记录 Bulk item 失败数量。
- 记录重建文档数量差异，方便发现 MySQL 快照与 ES 写入结果不一致。
- 所有指标通过现有 `/actuator/prometheus` 暴露，不引入新监控中间件。
- 所有指标标签保持低基数，不包含 `spuId`、`eventId`、关键词、分类、索引名、requestId 等动态值。

### 2.2 非目标

- 本轮不制作 Grafana Dashboard。
- 本轮不新增 Prometheus 告警规则。
- 本轮不新增数据库审计表或持久化统计表。
- 本轮不改变搜索、同步、重建的业务行为和失败策略。
- 本轮不把 RabbitMQ 队列深度采集逻辑写进 Java；队列深度继续由 RabbitMQ Prometheus 插件负责。

## 3. 方案选择

### 3.1 采用：复用 `ApplicationMetrics` 作为商品搜索指标门面

现有项目已经有 Micrometer、Actuator、Prometheus 端点和 `ApplicationMetrics`。P4 继续在这个门面里增加商品搜索相关方法，业务代码只调用固定语义的方法，不直接拼接动态指标标签。

优点：

- 与 AI、Redis、订单、支付指标风格一致；
- 低基数标签可以集中白名单校验；
- 不需要新增配置开关或监控依赖；
- 单元测试可以用 `SimpleMeterRegistry` 直接验证指标是否写入。

### 3.2 不采用：每个业务类直接注入 `MeterRegistry`

这种方式改动看似少，但标签白名单分散在多个类里，后续很容易把关键词、SPU ID、索引名等动态值误放进标签，造成 Prometheus 时序数量失控。

### 3.3 不采用：本轮直接做 Dashboard 和告警

Dashboard 和告警需要先有稳定的指标命名。P4 先交付指标与文档，等真实运行一段时间后，再根据基线补 Grafana 面板和告警阈值。

## 4. 指标设计

### 4.1 搜索引擎查询

```text
app.product.search.engine.requests{engine, outcome}
app.product.search.engine.duration{engine, outcome}
```

- `engine` 只允许：`elasticsearch`、`mysql`、`other`。
- `outcome` 只允许：`success`、`unavailable`、`error`、`other`。
- Elasticsearch 连接失败、5xx、404 别名缺失等可降级故障记录为 `unavailable`。
- Elasticsearch 400 类非降级错误记录为 `error` 并继续抛出。
- MySQL 查询成功记录为 `success`；MySQL 查询异常记录为 `error`。

### 4.2 搜索降级

```text
app.product.search.fallbacks{reason}
```

- `reason` 只允许：`unavailable`、`other`。
- 只有 `ProductSearchService` 捕获 `ProductSearchUnavailableException` 并改走 MySQL 时递增。
- 不按关键词、分类或异常消息打标签。

### 4.3 增量同步消费

```text
app.product.search.sync.consume{outcome}
app.product.search.sync.consume.duration{outcome}
app.product.search.sync.retries{stage}
```

- `outcome` 只允许：`success`、`duplicate`、`retry`、`dlq`、`error`、`other`。
- `stage` 只允许：`1`、`2`、`3`、`other`。
- 正常投影并写 Inbox 后记录 `success`。
- Inbox 已存在或并发唯一键冲突记录 `duplicate`。
- 临时故障进入 10 / 60 / 300 秒重试队列记录 `retry`，并在 `retries{stage}` 里记录下一阶段。
- 非法消息、永久 ES 4xx、超过第 3 阶段后进入 DLQ 记录 `dlq`。

### 4.4 全量重建

```text
app.product.search.rebuild.executions{outcome}
app.product.search.rebuild.duration{outcome}
app.product.search.rebuild.bulk.failures
app.product.search.rebuild.document.drift
```

- `outcome` 只允许：`success`、`error`、`other`。
- `executions` 与 `duration` 记录每次重建最终结果和耗时。
- `bulk.failures` 累加 Bulk 响应中失败的 item 数量。
- `document.drift` 记录 `abs(actualCount - expectedCount)`；只有数量不一致时记录，指标值不带索引名。

## 5. 模块接入点

### 5.1 `ElasticsearchProductSearchGateway`

在调用 ES Java Client 前后测量耗时：

- 成功返回：`recordProductSearchEngine("elasticsearch", "success", duration)`；
- `IOException` 或可降级 `ElasticsearchException`：记录 `unavailable` 后抛出 `ProductSearchUnavailableException`；
- 不可降级 `ElasticsearchException` 和其他运行时错误：记录 `error` 后继续抛出。

### 5.2 `MySqlProductSearchGateway`

在 MySQL LIKE 查询前后测量耗时：

- 成功返回：`recordProductSearchEngine("mysql", "success", duration)`；
- 查询异常：记录 `error` 后继续抛出。

### 5.3 `ProductSearchService`

只在实际降级时记录：

```text
recordProductSearchFallback("unavailable")
```

这样 ES 关闭、主搜索本来就是 MySQL 的场景不会被误记为降级。

### 5.4 `ProductSearchWorker`

`handle(...)` 从开始处理到 ACK 前统计一次结果：

- 解析失败直接 DLQ：`dlq`；
- Inbox 已处理：`duplicate`；
- 投影 + 版本 + Inbox 成功：`success`；
- 并发 Inbox 唯一键冲突：`duplicate`；
- 进入重试队列：`retry` 和对应 `stage`；
- 进入 DLQ：`dlq`。

如果发布重试 / DLQ 消息本身抛出异常，则不吞掉异常；此时记录 `error`，避免 ACK 后丢消息。

### 5.5 `ProductSearchIndexService`

`rebuild()` 入口到 `finally` 统计整次耗时：

- 完整完成并返回结果：`success`；
- 任意异常：`error`；
- Bulk 响应包含错误 item：先记录失败 item 数，再抛出原有异常；
- 文档数量不一致：先记录差异值，再抛出原有异常。

历史索引清理失败仍沿用 P1 策略：重建已经成功时只记录日志，不把成功结果改成失败指标。

## 6. 错误处理与一致性

- 指标记录失败不得改变业务结果。Micrometer 正常情况下不会抛出业务异常；如果未来替换 Registry，也不应让指标写入阻断搜索链路。
- 指标不参与事务，不作为搜索同步是否成功的事实依据。
- Worker 指标以“最终路由动作”为准：一条消息本次处理进入重试就记 `retry`，最终进入 DLQ 的那次再记 `dlq`。
- 重建文档差异指标只说明本次快照和新索引计数不一致，不负责自动修复；修复仍依赖失败清理、重新重建和人工排查。

## 7. 测试设计

- `ApplicationMetricsTests`：验证新增商品搜索指标、低基数标签白名单和动态标签收敛到 `other`。
- `ProductSearchServiceTests`：验证 ES 不可用时记录降级次数，普通编程错误不记录降级。
- `ProductSearchWorkerTests`：验证成功、重复、重试、DLQ 分支写入对应指标。
- `ProductSearchIndexServiceTests`：验证重建成功 / 失败耗时指标、Bulk item 失败数量、文档数量差异。
- 必要时新增 `ElasticsearchProductSearchGatewayTests` 和 `MySqlProductSearchGatewayTests`，专门覆盖网关耗时指标，避免只在编排层测试导致 ES / MySQL 标签混淆。

## 8. 文档与验收

文档更新位置：

- `docs/elasticsearch/README.md`：补充本地如何查看 `/actuator/prometheus` 中的商品搜索指标。
- `docs/elasticsearch/development-backlog.md`：P4 完成后将状态改为“已完成”，写入验收记录。

验收标准：

- `/actuator/prometheus` 可以看到商品搜索相关指标名。
- 模拟 ES 不可用后，搜索仍回退 MySQL，并记录 fallback。
- 模拟 Worker 重试和 DLQ 分支后，指标分别递增。
- 模拟 Bulk item 失败和文档数量不一致后，指标分别递增并保持原有失败行为。
- 指标标签不包含高基数字段。
- `backend\mvnw.cmd verify`、`docker compose config --quiet` 和 `git diff --check` 通过。

## 9. 后续演进

P4 完成并运行一段时间后，再进入下一轮：

- Grafana 商品搜索面板；
- Prometheus 告警规则；
- RabbitMQ 队列深度与 DLQ 堆积告警；
- 重建成功率、P95/P99 耗时和文档差异趋势；
- 结合日志 requestId 定位单次慢搜索。
