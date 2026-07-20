# Elasticsearch 商品索引生命周期实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 让商品索引重建在失败时删除本次半成品，并在成功后只保留当前索引和最近两个历史索引。

**Architecture:** 新增独立的 `ProductSearchIndexLifecycleService` 封装索引枚举、严格名称校验和删除决策；`ProductSearchIndexService` 只负责在失败和成功边界调用它。所有清理均为 best-effort，不能覆盖原始重建错误或破坏已经成功的别名切换。

**Tech Stack:** Java 21、Spring Boot 4、Elasticsearch Java Client 9.4.3、JUnit 5、AssertJ、Mockito、Maven。

---

### Task 1：保留数量配置

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchSearchProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchSearchConfigurationTests.java`

- [x] 先增加测试，断言默认保留两个历史索引且负数配置启动失败。
- [x] 运行测试，确认因缺少属性或校验而失败。
- [x] 增加 `retainedHistoryCount` 和 `@Min(0)` 校验，环境变量为 `APP_ELASTICSEARCH_RETAINED_HISTORY_COUNT`。
- [x] 运行配置测试并提交中文 Git 提交。

### Task 2：安全计算历史索引删除集合

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexDescriptor.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexRetentionPolicy.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexRetentionPolicyTests.java`

- [x] 先增加测试，覆盖当前索引保护、最近两个历史索引保留、严格 14 位时间戳和异常名称排除。
- [x] 运行测试，确认新类型不存在。
- [x] 实现纯 Java 保留策略，按创建时间降序选择删除集合，并写明双重删除保护原因。
- [x] 运行策略测试并提交中文 Git 提交。

### Task 3：实现 Elasticsearch 生命周期操作

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexLifecycleService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexLifecycleServiceTests.java`

- [x] 先增加测试，覆盖失败索引幂等删除、别名目标读取、超限历史索引删除和非受管索引保护。
- [x] 运行测试，确认服务不存在。
- [x] 使用官方 Client 实现索引查询和删除；任何删除前再次应用严格保留策略。
- [x] 运行生命周期服务测试并提交中文 Git 提交。

### Task 4：接入重建成功与失败边界

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`

- [x] 先增加编排测试：Bulk/数量校验失败只清理本次索引；创建失败不删除；失败清理不覆盖原异常；成功清理失败仍返回成功。
- [x] 运行测试，确认现有重建服务不满足行为。
- [x] 注入生命周期服务，记录索引是否已创建，并在 catch/finally 边界执行 best-effort 清理。
- [x] 运行重建服务测试并提交中文 Git 提交。

### Task 5：文档和端到端验证

**Files:**
- Modify: `docs/elasticsearch/README.md`
- Modify: `docs/elasticsearch/development-backlog.md`

- [x] 连续执行四次真实重建，确认别名只有一个目标且受管索引最多三个。
- [x] 验证文档数量和中文搜索结果。
- [x] 更新配置、清理行为、人工回滚说明和 P1 状态。
- [x] 运行 `backend\\mvnw.cmd verify` 与 `git diff --check`。
- [x] 使用中文 Git 提交完成文档和验证闭环。
