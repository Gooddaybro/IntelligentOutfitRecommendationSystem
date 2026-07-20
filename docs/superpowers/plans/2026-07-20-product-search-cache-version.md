# Product Search Cache Version Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 使用 MySQL 持久化全局版本构造商品搜索 Redis 缓存键，使增量投影和全量重建完成后不再命中旧搜索缓存。

**Architecture:** 新增单行 `product_search_cache_state` 和窄接口版本服务。搜索请求先读版本再生成 `product:search-versioned:v{generation}:...` 键；Worker 在 ES 投影成功后用同一事务递增版本并写 Inbox；全量重建在别名切换和 W0/W1 补偿完成后递增一次版本。

**Tech Stack:** Java 21、Spring Boot 4、MyBatis、Flyway、MySQL/H2、Spring Transaction、Redis、Elasticsearch Java Client、JUnit 5、Mockito、Maven。

---

## 文件结构

### 新增文件

- `backend/src/main/resources/db/migration/V25__product_search_cache_version.sql`：创建并初始化唯一版本状态行。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheVersionMapper.java`：读取和递增版本。
- `backend/src/main/resources/mapper/product/ProductSearchCacheVersionMapper.xml`：版本 SQL。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheVersionService.java`：校验状态行并暴露版本接口。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheKeyFactory.java`：集中生成带版本的搜索缓存键。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchConsumptionRecorder.java`：在一个事务中递增版本并写 Inbox。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheVersionPersistenceTests.java`：迁移、Mapper 和事务回滚测试。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheKeyFactoryTests.java`：键格式测试。

### 修改文件

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`：读取版本并使用新版缓存键。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java`：成功投影后调用事务记录器。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`：重建完成后递增版本。
- 对应现有单元测试：更新构造器并验证调用顺序和次数。
- `docs/elasticsearch/README.md`、`docs/elasticsearch/development-backlog.md`：记录 P3 配置、排障和验收结果，并修复 P2 状态段落的乱码。

---

### Task 1：持久化版本状态与版本服务

**Files:**
- Create: `backend/src/main/resources/db/migration/V25__product_search_cache_version.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheVersionMapper.java`
- Create: `backend/src/main/resources/mapper/product/ProductSearchCacheVersionMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheVersionService.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheVersionPersistenceTests.java`

- [x] **Step 1：先写失败的持久化测试**

测试必须验证初始版本为 1、连续递增不丢失，以及状态行被删除后服务拒绝返回猜测值：

```java
@Test
void initializesAndIncrementsDurableGeneration() {
    assertThat(versionService.currentVersion()).isEqualTo(1L);
    versionService.incrementVersion();
    versionService.incrementVersion();
    assertThat(versionService.currentVersion()).isEqualTo(3L);
}

@Test
void rejectsMissingStateRow() {
    jdbcTemplate.update("DELETE FROM product_search_cache_state WHERE id = 1");
    assertThatThrownBy(versionService::currentVersion)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cache version");
}
```

- [x] **Step 2：运行测试确认因表和类型缺失而失败**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ProductSearchCacheVersionPersistenceTests test
```

Expected: 编译失败或 Flyway/Bean 加载失败，明确指出 V25 表或版本类型尚不存在。

- [x] **Step 3：实现迁移、Mapper 和服务**

迁移核心内容：

```sql
CREATE TABLE product_search_cache_state (
    id BIGINT NOT NULL,
    generation BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT chk_product_search_cache_generation CHECK (generation > 0)
);

INSERT INTO product_search_cache_state (id, generation) VALUES (1, 1);
```

Mapper 契约：

```java
@Mapper
public interface ProductSearchCacheVersionMapper {
    Long findCurrentVersion();
    int incrementVersion();
}
```

SQL 必须固定 `id = 1`：

```xml
<select id="findCurrentVersion" resultType="java.lang.Long">
    SELECT generation FROM product_search_cache_state WHERE id = 1
</select>
<update id="incrementVersion">
    UPDATE product_search_cache_state
    SET generation = generation + 1, updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = 1
</update>
```

服务必须验证读取结果为正数、更新行数恰好为 1：

```java
@Service
public class ProductSearchCacheVersionService {
    public long currentVersion() { /* null/非正数时抛 IllegalStateException */ }
    public void incrementVersion() { /* updated != 1 时抛 IllegalStateException */ }
}
```

- [x] **Step 4：运行持久化测试并提交**

Run: `.\mvnw.cmd -Dtest=ProductSearchCacheVersionPersistenceTests test`

Expected: PASS。

Commit:

```powershell
git add backend/src/main/resources/db/migration/V25__product_search_cache_version.sql backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache backend/src/main/resources/mapper/product/ProductSearchCacheVersionMapper.xml backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheVersionPersistenceTests.java
git commit -m "功能：新增商品搜索缓存持久化版本"
```

---

### Task 2：版本化搜索缓存键

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheKeyFactory.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheKeyFactoryTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

- [x] **Step 1：先写键格式和服务缓存行为的失败测试**

```java
@Test
void differentVersionsUseDifferentNamespaces() {
    assertThat(factory.create(7, "coat", "外套"))
            .isEqualTo("product:search-versioned:v7:coat:外套");
    assertThat(factory.create(8, "coat", "外套"))
            .isEqualTo("product:search-versioned:v8:coat:外套");
}
```

在 `ProductCatalogServiceTests` 中把版本服务设为 7，并精确捕获 Redis 键：

```java
when(versionService.currentVersion()).thenReturn(7L);
service.searchProducts(" Coat ", " 外套 ");
verify(redisCacheService).getList(
        "product:search-versioned:v7:coat:外套", ProductSearchItem.class);
```

- [x] **Step 2：运行测试确认旧实现仍生成无版本键**

Run:

```powershell
.\mvnw.cmd '-Dtest=ProductSearchCacheKeyFactoryTests,ProductCatalogServiceTests' test
```

Expected: FAIL，键缺少 `v7` 或 Key Factory 尚不存在。

- [x] **Step 3：实现 Key Factory 并接入目录服务**

```java
@Component
public class ProductSearchCacheKeyFactory {
    public String create(long version, String normalizedKeyword, String normalizedCategory) {
        if (version <= 0) {
            throw new IllegalArgumentException("product search cache version must be positive");
        }
        return "product:search-versioned:v" + version + ":"
                + escapeQueryPart(normalizedKeyword) + ":"
                + escapeQueryPart(normalizedCategory);
    }
}
```

`ProductCatalogService.searchProducts` 的顺序固定为：规范化条件、读取版本、生成键、读缓存、真实搜索、写当前版本缓存。不得读取旧 `product:search:{query}` 键，也不得新增 `SCAN`/`KEYS`/通配符删除。

- [x] **Step 4：运行键和目录服务测试并提交**

Run: `.\mvnw.cmd '-Dtest=ProductSearchCacheKeyFactoryTests,ProductCatalogServiceTests' test`

Expected: PASS。

Commit:

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/cache/ProductSearchCacheKeyFactory.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheKeyFactoryTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java
git commit -m "功能：使用版本化商品搜索缓存键"
```

---

### Task 3：Worker 事务递增版本并写 Inbox

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchConsumptionRecorder.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheVersionPersistenceTests.java`

- [x] **Step 1：先写 Worker 和事务回滚失败测试**

Worker 成功测试必须验证投影后调用 Recorder，而不是直接插 Inbox：

```java
verify(projector).project(1001L);
verify(consumptionRecorder).record(message);
verify(channel).basicAck(7L, false);
```

集成测试先插入同一 Inbox 主键，再调用 Recorder，并验证版本不变：

```java
long before = versionService.currentVersion();
assertThatThrownBy(() -> recorder.record(message))
        .isInstanceOf(DuplicateKeyException.class);
assertThat(versionService.currentVersion()).isEqualTo(before);
```

- [x] **Step 2：运行测试确认 Recorder 尚不存在或版本未回滚**

Run:

```powershell
.\mvnw.cmd '-Dtest=ProductSearchWorkerTests,ProductSearchCacheVersionPersistenceTests' test
```

Expected: FAIL。

- [x] **Step 3：实现事务 Recorder 并修改 Worker**

```java
@Service
public class ProductSearchConsumptionRecorder {
    @Transactional
    public void record(ProductSearchSyncMessage message) {
        versionService.incrementVersion();
        inboxMapper.insert(CONSUMER_NAME, message.eventId(), message.spuId(), now());
    }
}
```

`CONSUMER_NAME` 必须由 Recorder 暴露为同一个包内可复用常量或由独立常量类集中定义，避免 Worker 重复字符串。Worker 保留消费前的 Inbox 快速检查；成功投影后调用 Recorder。`DuplicateKeyException` 仍 ACK，其他运行时异常沿用三级重试和 DLQ 策略。

- [x] **Step 4：运行 Worker 和事务测试并提交**

Run: `.\mvnw.cmd '-Dtest=ProductSearchWorkerTests,ProductSearchCacheVersionPersistenceTests' test`

Expected: PASS，重复事件版本不变。

Commit:

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchConsumptionRecorder.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchCacheVersionPersistenceTests.java
git commit -m "功能：同步提交搜索版本与消费记录"
```

---

### Task 4：全量重建完成后切换缓存版本

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`

- [x] **Step 1：先写重建版本递增失败测试**

在成功重建测试中注入 `ProductSearchCacheVersionService` Mock：

```java
service.rebuild();
InOrder order = inOrder(rebuildCompensator, versionService, lifecycleService);
order.verify(rebuildCompensator).compensateAfter(10L);
order.verify(versionService).incrementVersion();
order.verify(lifecycleService).pruneHistory();
```

再增加失败测试：版本递增抛错时重建向调用方返回该错误，并调用受保护的失败索引清理入口。

- [x] **Step 2：运行测试确认当前重建不会更新缓存版本**

Run: `.\mvnw.cmd -Dtest=ProductSearchIndexServiceTests test`

Expected: FAIL，`incrementVersion()` 未调用。

- [x] **Step 3：在补偿后、历史清理前递增一次版本**

生产构造器必须注入 `ProductSearchCacheVersionService`。成功路径固定为：

```java
switchAlias(indexName);
rebuildCompensator.ifPresent(compensator -> compensator.compensateAfter(startWatermark));
cacheVersionService.incrementVersion();
pruneHistoryWithoutBreakingRebuild();
```

不得在每个补偿 SPU 上递增版本，也不得在别名切换前递增。

- [x] **Step 4：运行重建与生命周期测试并提交**

Run:

```powershell
.\mvnw.cmd '-Dtest=ProductSearchIndexServiceTests,ProductSearchIndexLifecycleServiceTests' test
```

Expected: PASS。

Commit:

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java
git commit -m "功能：索引重建后切换搜索缓存版本"
```

---

### Task 5：回归、真实进程验收与文档闭环

**Files:**
- Modify: `docs/elasticsearch/README.md`
- Modify: `docs/elasticsearch/development-backlog.md`
- Modify: `docs/superpowers/specs/2026-07-20-product-search-cache-version-design.md`
- Modify: `docs/superpowers/plans/2026-07-20-product-search-cache-version.md`

- [x] **Step 1：运行缓存相关回归测试**

Run:

```powershell
cd backend
.\mvnw.cmd '-Dtest=ProductSearchCacheVersionPersistenceTests,ProductSearchCacheKeyFactoryTests,ProductCatalogServiceTests,ProductSearchWorkerTests,ProductSearchIndexServiceTests' test
```

Expected: 全部 PASS。

- [x] **Step 2：静态确认没有 Redis 模糊删除**

Run:

```powershell
$patterns = @(
  '\.(scan|keys)\s*\(',
  'redis\.call\([''"](SCAN|KEYS)[''"]',
  'RedisCommand\.(SCAN|KEYS)',
  'product:search(?:-versioned)?:[^''"]*\*'
)
$matches = Get-ChildItem src/main/java -Recurse -Filter *.java |
  Select-String -CaseSensitive -Pattern $patterns
if ($matches) {
  $matches
  throw '发现 Redis 模糊扫描或商品搜索通配删除'
}
```

Expected: 生产代码无匹配。

实际结果：上述精确命令无匹配，即没有 Redis `SCAN`、`KEYS` 命令、`.scan(...)` / `.keys(...)` 调用或
商品搜索通配删除。它不会把 JDBC 的 `RETURN_GENERATED_KEYS` 或限流 Lua 脚本的参数数组 `KEYS[1]` 误报成
Redis `KEYS` 命令。

- [x] **Step 3：执行真实进程验收**

启动 MySQL、Redis、RabbitMQ、Elasticsearch、Web 和 Worker。先记录：

```sql
SELECT generation FROM product_search_cache_state WHERE id = 1;
```

请求同一搜索条件两次，确认 Redis 存在当前版本键。修改商品并等待 Inbox 后，再读取版本，必须恰好增加 1；相同搜索条件必须创建新版本键。随后执行一次 `/internal/search/products/rebuild`，确认版本再次恰好增加 1，旧键仍存在但不再被读取。

实际结果：generation 1 下首次请求写入 v1 键，第二次请求使 Redis hit 计数增加；合法事件完成
`NEW → PUBLISHED → Inbox` 后 generation 恰好变为 2；重复发布同一 `eventId` 后仍为 2；重建切换别名、
校验 40 个文档后恰好变为 3。相同查询的 v1、v2、v3 键曾同时存在，当前请求只创建/读取 v3 键。

- [x] **Step 4：更新文档并修复 P2 状态乱码**

README 必须说明版本表、键格式、更新时机、旧键 TTL 回收和排障 SQL。Backlog 把 P3 标记为“已完成”，同时恢复 P2 已交付范围和验收记录的可读中文，不保留问号乱码。

- [x] **Step 5：运行完整验证**

Run:

```powershell
.\mvnw.cmd verify
git diff --check
docker compose config --quiet
```

Expected: Maven BUILD SUCCESS、0 Checkstyle violations、Git whitespace 检查和 Compose 配置检查均成功。

实际结果：`mvnw.cmd verify` 运行 402 项测试，0 失败、0 错误、4 跳过；Checkstyle 0 违规；
`git diff --check` 与 `docker compose config --quiet` 均成功。

- [x] **Step 6：中文提交文档与验收记录**

```powershell
git add docs/elasticsearch/README.md docs/elasticsearch/development-backlog.md docs/superpowers/specs/2026-07-20-product-search-cache-version-design.md docs/superpowers/plans/2026-07-20-product-search-cache-version.md
git commit -m "测试：完成搜索缓存版本化验收"
```
