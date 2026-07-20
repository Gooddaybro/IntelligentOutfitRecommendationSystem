# Elasticsearch 商品搜索待开发清单

本文档记录 C1 完成后的后续开发顺序。状态只表示代码是否已经交付，不表示方案是否被放弃。

| 优先级 | 能力 | 当前状态 | 验收目标 |
|---|---|---|---|
| P1 | 索引生命周期治理 | 已完成 | 失败重建删除本次半成品；成功后保留当前索引和最近两个历史索引 |
| P2 | Outbox + RabbitMQ 增量同步 | 已完成 | 商品事务提交后可靠、幂等地更新对应 SPU 文档，全量重建继续作为修复手段 |
| P3 | 搜索缓存版本化 | 已完成 | 增量投影或别名切换后，无需 Redis 模糊删除即可立即使用新搜索结果 |
| P4 | 可观测性 | 待开发 | 记录 ES 搜索耗时、降级次数、重建耗时、Bulk 失败和文档数量差异 |

## P1 已确认约束

- 当前查询别名指向的索引永远不能被清理任务删除；
- 成功重建后保留当前索引和按创建时间排序的最近两个历史索引；
- 重建失败只清理本次创建的物理索引，不触碰任何旧索引；
- 只能删除名称严格符合配置前缀和 UTC 时间戳后缀的索引；
- 清理属于维护动作：清理失败不得破坏已经成功完成的别名切换，但必须记录明确日志。

## P1 验证记录

本地连续执行四次 Java 全量重建后：

- `product_current` 只指向一个索引；
- 受管时间戳索引保持为三个，即当前索引和两个历史索引；
- 不符合严格命名规则的 `product_v1` 未被删除；
- 当前索引包含 40 个 MySQL 投影文档；
- “通勤外套”中文查询仍按相关度返回商品。

## P2 已交付范围

- 使用独立的 `product_search_outbox` / `product_search_inbox`，不复用 AI 异步任务表；
- 商品新增、编辑、上下架和分类改名在业务事务内写 Outbox，价格与库存变化不写搜索事件；
- 使用独立的 Product Search Exchange、主队列、10/60/300 秒重试队列和 DLQ；
- Relay 只有在 Publisher Confirm 成功且消息未被 Return 时才标记 `PUBLISHED`；
- Worker 每次按 SPU ID 读取 MySQL 最新事实，以固定 SPU `_id` 幂等 UPSERT/DELETE，并用 Inbox 去重；
- 全量重建通过 W0/W1 水位补偿别名切换窗口内的并发变更。

## P2 验证记录

- Web、Worker、MySQL、RabbitMQ 与 Elasticsearch 真实进程链路已通过；
- 合法事件可以从 `NEW` 进入 `PUBLISHED`，Worker 完成 ES 投影并写 Inbox；
- 重复消息不重复投影，Elasticsearch 暂停后的消息会进入重试且恢复后不丢失；
- 重建期间产生的变更可通过 `(W0, W1]` 去重补偿到新别名目标；
- 全量重建仍保留，继续负责 Mapping 升级、数据校正和灾难恢复。

## P3 已交付范围

- 新增单行持久化表 `product_search_cache_state`，所有实例共享正整数 `generation`；
- 搜索缓存使用 `product:search-versioned:v{generation}:{escapedKeyword}:{escapedCategory}` 独立命名空间；
- Worker 在同一个 MySQL 事务内递增版本并写 Inbox，重复事件导致的唯一键冲突会整体回滚；
- 全量重建在别名切换和 W0/W1 补偿完成后只递增一次版本；
- 旧版本键由原有 TTL 自然回收，不使用 Redis `KEYS`、`SCAN` 或通配符删除。

## P3 验证记录

2026-07-20 使用 MySQL、Redis、RabbitMQ、Elasticsearch、Web 和 Worker 真实进程完成验收：

- generation 1 的相同搜索连续请求，第一次写入缓存，第二次 Redis 命中；
- 合法商品事件 `NEW → PUBLISHED → Inbox` 后 generation 从 1 恰好递增到 2；
- 直接重复投递同一 `eventId` 后 generation 仍为 2，Inbox 仍只有一条；
- 全量重建切换 `product_current` 后 generation 从 2 恰好递增到 3，新索引包含 40 个文档；
- 同一搜索条件的 v1、v2、v3 键曾同时存在，请求只使用当前 v3 命名空间；验收结束后仅删除了专用测试键和事件数据。

## 后续入口

下一步单独设计 P4 可观测性，优先覆盖 ES 搜索耗时与降级、增量同步重试/DLQ、重建耗时、Bulk 失败和文档数量差异。
