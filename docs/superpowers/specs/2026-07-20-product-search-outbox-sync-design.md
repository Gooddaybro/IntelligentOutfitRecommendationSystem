# 商品搜索 Outbox 增量同步设计

## 1. 目标

在现有 Elasticsearch 全量重建和 MySQL 降级搜索基础上，增加可靠的商品增量同步。后台商品事务成功提交后，相关 SPU 最终写入 `product_current`；RabbitMQ 或 Elasticsearch 暂时不可用时事件不丢失，恢复后可以继续处理。

本阶段覆盖：

- 商品新增；
- 商品编辑；
- 商品上下架和删除状态；
- 分类名称修改后，该分类下全部 SPU 的重新投影；
- 全量重建与增量消费并发时的 Outbox 水位补偿。

价格、库存、订单和支付不进入 Elasticsearch 文档，因此这些变化不产生搜索同步事件。当前没有后台管理入口的材质、季节等直接数据库修改仍通过全量重建修复。

## 2. 独立消息边界

商品搜索不复用现有 AI Task Outbox。现有 `outbox_event` Relay 会把全部待发布事件固定发送到 AI 队列，直接复用会造成错误路由；重构通用平台又会扩大本阶段风险。

新增独立链路：

```text
后台商品事务
  ├─ 修改商品或分类业务表
  └─ 同事务写入 product_search_outbox
              ↓
ProductSearchOutboxRelay（web profile）
              ↓ Publisher Confirm
product.search.exchange
              ↓
product.search.queue
              ↓
ProductSearchWorker（worker profile）
              ↓
读取 MySQL 最新商品事实
              ↓
UPSERT / DELETE product_current
              ↓
写入 product_search_inbox
```

AI Task 的表、Exchange、Queue、Relay、Worker 和配置保持不变。

## 3. 数据表

### 3.1 product_search_outbox

关键字段：

```text
id                 BIGINT 自增水位
event_id           全局唯一事件 ID
spu_id             商品聚合 ID
event_type         PRODUCT_SEARCH_REINDEX_REQUESTED
schema_version     消息结构版本，首版为 1
payload            JSON 消息
status             NEW / PUBLISHED
available_at       最早发布时间
claimed_by         Relay 租约持有者
claim_until        Relay 租约到期时间
publish_attempts   发布尝试次数
published_at       Confirm 成功时间
last_error         最近发布错误摘要
created_at
updated_at
```

`event_id` 唯一；发布扫描使用 `(status, available_at, claim_until)` 索引；`spu_id, id` 索引用于重建水位补偿。

### 3.2 product_search_inbox

```text
consumer_name
event_id
spu_id
processed_at
PRIMARY KEY (consumer_name, event_id)
```

Inbox 只记录已经成功完成 ES 操作的事件。ES 成功但 Inbox 事务失败时，消息会再次处理；由于文档 ID 固定为 SPU ID，重复 UPSERT 或 DELETE 是幂等的。

## 4. 事件模型

消息只携带定位和审计信息：

```json
{
  "eventId": "UUID",
  "spuId": 1002,
  "eventType": "PRODUCT_SEARCH_REINDEX_REQUESTED",
  "occurredAt": "2026-07-20T08:00:00Z",
  "schemaVersion": 1
}
```

消息不携带商品名称、分类和标签等可变快照。Worker 按 `spuId` 查询 MySQL 最新投影，因此同一 SPU 的消息乱序到达时，较旧事件仍会写入当前事实，而不会用旧载荷覆盖新数据。

## 5. 事务事件产生

新增 `ProductSearchChangeRecorder` 业务边界，由 `AdminCatalogService` 在现有事务内调用：

- 商品创建完成并取得 SPU ID 后记录一条事件；
- 商品编辑、状态修改完成后记录一条事件；
- 分类名称修改前读取受影响 SPU ID，修改成功后逐个记录事件；
- 价格和库存管理方法不调用 Recorder。

当 `app.product-search-sync.enabled=false` 时注入 No-op Recorder，不写 Outbox，从而保持默认部署行为。开启后，业务修改与 Outbox INSERT 使用同一个数据库事务；任一操作失败时同时回滚。

## 6. Relay 与 RabbitMQ 拓扑

`ProductSearchOutboxRelay` 只在 `web` profile 且 publisher 开关开启时扫描商品表。每个 Relay 使用带过期时间的数据库租约抢占事件，发送时使用 `event_id` 作为 CorrelationData ID。

只有同时满足以下条件才标记 `PUBLISHED`：

- RabbitMQ Publisher Confirm 返回 ACK；
- 消息没有被 Return；
- Outbox 仍由当前 Relay 持有。

RabbitMQ 使用独立拓扑：

```text
product.search.exchange
├─ product.search.main       → product.search.queue
├─ product.search.retry.10s  → 10 秒延迟队列
├─ product.search.retry.60s  → 60 秒延迟队列
├─ product.search.retry.300s → 300 秒延迟队列
└─ product.search.dlq        → product.search.dlq
```

延迟队列到期后通过 DLX 回到主 Routing Key。拓扑名称通过常量统一管理，不与 AI Task 复用。

## 7. Worker 与投影

`ProductSearchWorker` 在 `worker` profile 且 listener 开关开启时消费。处理顺序：

1. 校验 JSON、事件类型和 schemaVersion；
2. 查询 Inbox，已处理事件直接 ACK；
3. 使用 `ProductMapper.findSearchIndexRowBySpuId` 读取 MySQL 最新事实；
4. 查到商品时，以 SPU ID 为 `_id` UPSERT 到 `product_current`；
5. 商品不存在时，从 `product_current` 幂等 DELETE；
6. ES 操作成功后写入 Inbox；
7. Inbox 事务提交后 ACK RabbitMQ 消息。

全量重建继续索引所有商品状态，查询时用 `status=on_sale` 过滤；因此下架、草稿和逻辑删除状态使用 UPSERT 更新状态，而不是删除文档。只有 MySQL 物理记录不存在时才删除 ES 文档。

## 8. 失败分类

允许重试：

- ES 连接、超时、429 和 5xx；
- 数据库临时连接错误；
- Worker 在成功提交前遇到的基础设施错误。

直接进入 DLQ：

- 非法 JSON；
- 不支持的事件类型或 schemaVersion；
- ES 400 类 Mapping、字段和查询错误；
- 重试阶段已经用尽。

Worker 使用手动 ACK。重试消息或 DLQ 消息发布成功前不 ACK 原消息，避免路由失败时丢失事件。

## 9. 全量重建水位补偿

增量 Worker 可能在重建期间把事件写到旧别名目标。重建流程增加两个 Outbox 水位：

```text
W0 = 重建开始前已提交的最大 product_search_outbox.id
→ 读取 MySQL 并构建新物理索引
→ 原子切换 product_current
W1 = 切换后立即读取的最大已提交 Outbox ID
→ 查询 W0 < id <= W1 的去重 SPU ID
→ 从 MySQL 读取最新事实并重新投影到新别名目标
```

正确性边界：

- 切换前已消费到旧索引的事件会被补偿到新索引；
- 切换后提交且 ID 大于 W1 的事件由 Worker 写入新别名；
- 补偿与 Worker 并发重复执行是幂等的；
- 未提交事务不会出现在 W1 中，提交后会由 Worker 正常处理；
- Outbox 记录不能在重建水位窗口内物理删除。本阶段不实现 Outbox 清理任务。

补偿失败发生在别名切换后，不能假装重建完全成功。接口返回明确失败并保留 Outbox 数据，运维可重试重建；增量 Worker仍继续处理新消息。

## 10. 功能开关

新增配置，默认全部关闭：

```text
app.product-search-sync.enabled=false
app.product-search-sync.publisher-enabled=false
app.product-search-sync.listener-enabled=false
```

以及批量大小、Relay 间隔、租约时间、Confirm 超时等有界参数。约束如下：

- `enabled=false`：使用 No-op Recorder，不写商品 Outbox；
- publisher 只在 `web` profile 启动；
- listener 只在 `worker` profile 启动；
- listener 开启时必须同时开启 Elasticsearch Client；
- 全量重建不依赖 publisher/listener，但启用同步功能后会使用 Outbox 水位补偿。

## 11. 缓存边界

本阶段只同步 Elasticsearch。现有 Redis 商品搜索缓存键和 TTL 不变，因此 ES 更新后接口仍可能在缓存有效期内返回旧搜索列表。立即可见性由后续 P3“搜索缓存版本化”解决，不能在 P2 中使用 Redis 通配删除扩大范围。

## 12. 测试与验收

自动测试覆盖：

- 商品事务与 Outbox 同时提交或回滚；
- 商品新增、编辑、状态修改分别产生事件；
- 分类名称修改为全部受影响 SPU 产生事件；
- 价格和库存变化不产生事件；
- Relay 租约竞争、Confirm ACK、NACK、Return 和超时；
- Inbox 重复事件跳过；
- 乱序消息始终投影 MySQL 最新事实；
- ES 暂时不可用进入分阶段重试；
- 非法消息和 ES 400 错误进入 DLQ；
- MySQL 物理记录不存在时删除 ES 文档；
- W0/W1 范围去重和别名切换后的补偿；
- 原有 AI Task 消息链路测试不变。

端到端验证：

1. 启动 MySQL、RabbitMQ、Elasticsearch；
2. 后台修改商品名称，确认 Outbox 从 NEW 变为 PUBLISHED；
3. Worker 消费后确认 ES 文档和 Inbox；
4. 重复投递同一事件，文档结果不变；
5. 停止 ES 后修改商品，确认事件进入重试而不丢失；
6. 恢复 ES 后确认最终同步；
7. 重建期间修改商品，确认切换后的索引包含修改；
8. 完整 `mvnw.cmd verify` 与 Checkstyle 通过。

## 13. 明确不做

- 不重构 AI Task Outbox；
- 不实现价格和库存同步；
- 不实现 Outbox/Inbox 定时清理；
- 不实现 Redis 搜索缓存版本化；
- 不实现材质、季节等尚无管理入口的数据变更事件；
- 不实现跨服务通用事件平台；
- 不取消全量重建和 MySQL 搜索降级。
