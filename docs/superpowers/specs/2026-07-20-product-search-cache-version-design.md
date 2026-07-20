# 商品搜索缓存版本化设计

## 1. 背景

商品搜索当前使用 `product:search:{keyword}:{category}` 形式的 Redis 缓存键。P2 已能通过独立 Outbox、RabbitMQ 和 Worker 将 MySQL 商品事实增量投影到 Elasticsearch，也能通过全量重建切换 `product_current`。但是 Elasticsearch 更新后，已有搜索缓存仍可能在 TTL 内返回旧结果。

P3 使用持久化全局版本构造搜索缓存命名空间，让新请求自然绕过旧缓存，而不是扫描或模糊删除 Redis 键。

## 2. 目标与非目标

### 2.1 目标

- 增量投影成功后，新请求不再命中投影前的搜索缓存。
- 全量重建完成别名切换和水位补偿后，新请求立即使用新的缓存命名空间。
- 多 Web、Relay 和 Worker 实例共享同一个单调递增版本。
- Redis 重启或短暂故障后不会因为版本状态丢失而重新使用旧缓存。
- Redis 仍是可降级的加速层，不执行通配符扫描或批量删除。

### 2.2 非目标

- 不修改 `product:detail:*` 商品详情缓存。
- 不修改推荐候选缓存。
- 不提前实现 P4 指标、Dashboard 和告警。
- 不保证关闭 P2 增量同步时 Elasticsearch 自动跟随商品修改。
- 不为缓存版本引入 Redis 分布式锁或本地多级缓存。

## 3. 方案选择

### 3.1 采用：MySQL 持久化全局版本

新增单行表 `product_search_cache_state`，以 `id = 1` 保存 `generation`。搜索请求读取当前版本并构造缓存键：

```text
product:search-versioned:v{generation}:{escapedKeyword}:{escapedCategory}
```

MySQL 是现有商品搜索实时补齐所依赖的事实基础。每次搜索增加一次主键单行查询，但能获得跨进程、跨 Redis 重启的稳定一致性边界。

### 3.2 不采用：Redis `INCR`

Redis 计数器实现较轻，但 Redis 故障时版本递增可能丢失。Redis 恢复后，旧计数器和旧缓存仍可能重新被命中，只能提供“最多一个 TTL”的弱保证。

### 3.3 不采用：维护搜索键集合并逐个删除

任意关键词和分类组合会形成大量动态键。维护反向集合会增加写放大、残留清理和并发一致性问题，也不能可靠覆盖未来新增的搜索条件。

## 4. 数据模型

新增 Flyway 迁移，创建：

```sql
CREATE TABLE product_search_cache_state (
    id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
);
```

迁移同时插入唯一状态行：

```text
id = 1
generation = 1
```

版本递增使用条件明确的单行原子更新。若状态行缺失或更新行数不是 1，视为配置或数据损坏，不自动重建状态行。

## 5. 模块边界

### 5.1 `ProductSearchCacheVersionMapper`

只负责读取当前 `generation` 和原子递增 `generation`，不接触 Redis，也不生成缓存键。

### 5.2 `ProductSearchCacheVersionService`

提供两个窄接口：

```text
currentVersion()
incrementVersion()
```

`currentVersion()` 必须返回正整数；`incrementVersion()` 必须确认恰好更新一行。状态异常时抛出明确错误。

### 5.3 `ProductSearchCacheKeyFactory`

负责把版本号与已经规范化的查询条件拼接为：

```text
product:search-versioned:v{version}:{escapedKeyword}:{escapedCategory}
```

该组件不读取数据库或 Redis，使键格式能够单独测试，避免版本逻辑散落在 `ProductCatalogService`。独立的
`product:search-versioned:` 前缀与旧 `product:search:` 命名空间隔离；关键词和分类中的 `%`、`:` 会依次转义为
`%25`、`%3A`，避免不同输入组合产生同一个 Redis 键。

### 5.4 `ProductSearchConsumptionRecorder`

Worker 成功投影 ES 后，通过该事务组件完成：

1. 递增搜索缓存版本；
2. 插入 Inbox。

两个数据库操作必须位于同一事务。若并发消费者导致 Inbox 唯一键冲突，整个事务回滚，因此不会留下重复版本递增。

## 6. 数据流

### 6.1 搜索读取

```text
ProductCatalogService
    → ProductSearchCacheVersionService.currentVersion()
    → ProductSearchCacheKeyFactory.create(version, keyword, category)
    → RedisCacheService.getList(...)
        ├── 命中：返回缓存
        └── 未命中或 Redis 异常：查询 ProductSearchService
                                  → 写入当前版本缓存
```

旧版本键不删除，由现有搜索缓存 TTL 自动回收。

MySQL 版本读取失败时，不猜测版本、不回退到固定版本，也不读取无版本缓存。商品搜索本身需要 MySQL 补齐实时状态和价格，因此此时按数据库故障返回错误。

### 6.2 增量投影

```text
RabbitMQ message
    → Inbox 重复检查
    → 从 MySQL 读取当前 SPU 事实
    → UPSERT/DELETE product_current 文档
    → ProductSearchConsumptionRecorder
        → generation + 1
        → INSERT Inbox
    → ACK
```

若 ES 投影失败，不递增版本、不写 Inbox。若版本或 Inbox 事务失败，原消息进入既有重试策略；重复 ES 投影按固定 SPU `_id` 保持幂等。

### 6.3 全量重建

```text
记录 W0
→ 读取 MySQL 快照
→ 创建并填充新物理索引
→ 校验文档数量
→ 原子切换 product_current
→ 补偿 (W0, W1] 的不同 SPU
→ generation + 1
→ 清理历史索引
```

全量重建只递增一次版本，不按补偿 SPU 数量重复递增。

版本递增失败时，重建接口返回明确失败。由于别名可能已经完成切换，失败清理仍必须遵守现有保护规则，不得删除当前别名目标。

## 7. 并发与一致性

- `generation = generation + 1` 由 MySQL 原子执行，多 Worker 不会丢失递增。
- Worker 的版本递增与 Inbox 插入处于同一事务，重复事件不会产生多余版本。
- ES 与 MySQL 无法组成单个分布式事务，因此 ES 成功到版本提交之间存在极短窗口。事务失败时消息会重试，最终关闭窗口。
- 缓存版本是全局版本。任何一个 SPU 投影都会使全部搜索结果进入新命名空间，以简单性换取可证明的正确性。
- 旧版本键继续占用 Redis 空间直到 TTL 到期，不影响读取正确性。

## 8. 配置与兼容性

- 不新增默认开启的功能开关；版本化成为商品搜索缓存的固定键格式。
- 数据库迁移把初始版本设为 1，部署后旧的无版本缓存不会再被读取。
- 原有缓存 TTL 和抖动策略保持不变。
- P2 增量同步开启时，Worker 负责变更后的版本递增；关闭 P2 时，不承诺 ES 自动更新，也不额外伪造版本变更。

## 9. 错误处理

- Redis 读写异常：沿用当前降级策略，绕过缓存并继续搜索。
- 版本状态读取失败或状态行缺失：按数据库错误失败，不使用旧缓存。
- Worker 版本事务失败：按现有临时故障策略重试，发布重试消息成功后才 ACK。
- Inbox 唯一键冲突：视为并发重复消费；事务回滚后 ACK，不再次递增版本。
- 全量重建版本递增失败：返回失败并保留已经切换的当前索引。

## 10. 测试与验收

### 10.1 自动化测试

- Migration/Mapper 测试：初始版本、读取、连续原子递增。
- Key Factory 测试：不同版本生成不同键，相同输入生成稳定键。
- ProductCatalogService 测试：读取当前版本、命中当前版本缓存、旧版本缓存不可见。
- Worker 测试：成功投影后递增并写 Inbox；重复事件不投影、不递增。
- 事务测试：Inbox 唯一键冲突会回滚版本递增。
- 重建测试：别名切换和 W0/W1 补偿完成后只递增一次。
- 回归测试：Redis 异常仍执行真实搜索，旧无版本键不再使用。

### 10.2 真实进程验收

1. 在 Redis 写入版本 1 的搜索结果并确认命中。
2. 修改商品并等待 P2 Worker 完成，确认 MySQL 版本递增。
3. 使用相同搜索条件请求，确认读取新版本键并获得新结果。
4. 执行全量重建，确认别名切换后版本只增加一次。
5. 确认 Redis 中旧键仍可存在，但不再收到读取流量。

## 11. 完成标准

- 搜索缓存键包含 MySQL 持久化版本。
- 增量投影和 Inbox 成功提交时版本递增一次。
- 重复事件不重复递增。
- 全量重建成功后版本递增一次。
- 不存在 Redis `KEYS`、`SCAN` 或通配符删除逻辑。
- Redis 故障不阻断真实搜索。
- Maven Verify、Checkstyle 和 `git diff --check` 全部通过。
