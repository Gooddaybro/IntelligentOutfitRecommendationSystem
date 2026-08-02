# 商品搜索 Outbox 增量同步实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 商品和分类事务提交后，通过独立 Outbox 与 RabbitMQ 可靠、幂等地把 MySQL 最新商品事实同步到 Elasticsearch。

**Architecture:** 商品业务事务只写独立 `product_search_outbox`；Web Relay 经 Publisher Confirm 发布到独立 RabbitMQ 拓扑，Worker 读取 MySQL 最新投影并写入 `product_current`，成功后记录 Inbox。全量重建使用 Outbox ID 水位补偿别名切换窗口内的并发修改。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis/JdbcTemplate、MySQL/H2、RabbitMQ、Elasticsearch Java Client 9.4.3、JUnit 5、Mockito、Maven。

---

### Task 1：数据库结构与消息配置

**Files:**
- Create: `backend/src/main/resources/db/migration/V24__product_search_outbox_inbox.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchSyncProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchSyncPersistenceTests.java`

- [x] 先写持久化测试，断言 Outbox 唯一键、发布扫描索引语义和 Inbox 去重。
- [x] 运行测试，确认表和 Mapper 缺失。
- [x] 创建独立 Outbox/Inbox 表和默认关闭、带合法范围校验的配置。
- [x] 运行数据库迁移与配置测试，中文提交。

### Task 2：Outbox 模型、Mapper 与事务 Recorder

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchOutboxEvent.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchSyncMessage.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchOutboxMapper.java`
- Create: `backend/src/main/resources/mapper/product/ProductSearchOutboxMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchChangeRecorder.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/OutboxProductSearchChangeRecorder.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/NoOpProductSearchChangeRecorder.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchChangeRecorderTests.java`

- [x] 先写失败测试，定义 `record(spuId)`、最大水位和区间去重 SPU 查询契约。
- [x] 实现 schemaVersion=1 的最小消息和同事务 Outbox INSERT。
- [x] 使用条件 Bean 在总开关关闭时提供 No-op Recorder。
- [x] 验证序列化、唯一事件 ID、水位查询和事务回滚，中文提交。

### Task 3：接入后台商品与分类事务

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminCatalogServiceTests.java`

- [x] 先写测试，证明创建、编辑、状态修改记录 SPU，分类修改记录全部受影响 SPU，库存调整不记录。
- [x] 注入 Recorder，在业务 SQL 成功后、事务返回前记录事件。
- [x] 分类更新前读取受影响 SPU ID，并在同一事务逐个记录。
- [x] 运行 Admin 服务和事务测试，中文提交。

### Task 4：独立 RabbitMQ 拓扑与可靠 Relay

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/RabbitProductSearchTopology.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchOutboxRelay.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/RabbitProductSearchTopologyTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchOutboxRelayTests.java`

- [x] 先写拓扑和 Relay 失败测试，覆盖租约竞争、Confirm ACK、NACK、Return 与超时。
- [x] 声明主队列、10/60/300 秒延迟队列和 DLQ。
- [x] Relay 只在 web profile、总开关和 publisher 开关开启时工作。
- [x] 只有 Confirm ACK 且没有 Return 才标记 PUBLISHED，中文提交。

### Task 5：单 SPU 投影、Inbox 与 Worker

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- Modify: `backend/src/main/resources/mapper/product/ProductMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchIncrementalProjector.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchInboxMapper.java`
- Create: `backend/src/main/resources/mapper/product/ProductSearchInboxMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchFailureClassifier.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIncrementalProjectorTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java`

- [x] 先写测试，覆盖最新事实 UPSERT、物理缺失 DELETE、重复事件、乱序事件、分级重试和 DLQ。
- [x] 增加按 SPU ID 读取完整索引投影的 Mapper 查询。
- [x] Projector 使用 `product_current` 和固定 SPU `_id` 执行幂等操作。
- [x] Worker 在 ES 成功后写 Inbox，发布重试/DLQ 成功后才 ACK 原消息。
- [x] 运行 Worker、投影和 Mapper 测试，中文提交。

### Task 6：全量重建水位补偿

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`

- [x] 先写测试，证明 W0/W1 区间去重、切换后补偿和同步关闭时跳过水位逻辑。
- [x] 重建开始读取 W0，别名切换后读取 W1，并投影 `(W0, W1]` 的不同 SPU。
- [x] 补偿失败返回明确错误，不删除已经成为当前别名目标的新索引。
- [x] 运行重建、生命周期和并发补偿测试，中文提交。

### Task 7：端到端验证与文档闭环

**Files:**
- Modify: `docs/elasticsearch/README.md`
- Modify: `docs/elasticsearch/development-backlog.md`
- Modify: `docs/superpowers/plans/2026-07-20-product-search-outbox-sync.md`

- [x] 启动 MySQL、RabbitMQ、Elasticsearch，并验证独立拓扑。
- [x] 修改商品，验证 Outbox、RabbitMQ、Inbox 和 ES 最终状态。
- [x] 验证重复消息、ES 停止恢复和重建水位补偿。
- [x] 更新配置、运维、DLQ 和已知 Redis 缓存窗口说明。
- [x] 运行 `backend\\mvnw.cmd verify`、Checkstyle 和 `git diff --check`，中文提交。
