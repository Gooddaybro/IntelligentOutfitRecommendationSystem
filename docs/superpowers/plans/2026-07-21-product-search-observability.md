# Product Search Observability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Elasticsearch 商品搜索链路补齐低基数 Prometheus 指标，覆盖搜索耗时、降级次数、同步重试 / DLQ、重建耗时、Bulk 失败和文档数量差异。

**Architecture:** 复用现有 `ApplicationMetrics` 作为唯一指标门面，业务类只提交固定枚举语义，不直接拼接 Micrometer 标签。ES / MySQL 网关记录搜索引擎耗时，编排层记录真实降级，Worker 记录本次消费最终动作，重建服务记录整次重建结果、Bulk item 失败数和文档 drift。

**Tech Stack:** Java 21、Spring Boot 4、Micrometer、Actuator Prometheus、Elasticsearch Java Client、RabbitMQ、MyBatis、JUnit 5、Mockito、AssertJ、Maven。

---

## 文件结构

### 修改文件

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`：新增商品搜索指标白名单和记录方法。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchProductSearchGateway.java`：记录 ES 查询耗时和 outcome。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/MySqlProductSearchGateway.java`：记录 MySQL LIKE 查询耗时和 outcome。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchService.java`：记录真实 fallback 次数。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchServiceConfiguration.java`：向搜索服务注入 `ApplicationMetrics`。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java`：记录消费成功、重复、重试和 DLQ。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`：记录重建耗时、Bulk 失败 item 数和文档数量差异。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`：验证新增指标和标签白名单。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchServiceTests.java`：验证 fallback 指标。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java`：验证 Worker 指标。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`：验证重建指标。
- `docs/elasticsearch/README.md`：补充本地指标查看方式。
- `docs/elasticsearch/development-backlog.md`：P4 完成后更新状态和验收记录。

### 新增文件

- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchProductSearchGatewayTests.java`：聚焦验证 ES 网关指标。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/MySqlProductSearchGatewayTests.java`：聚焦验证 MySQL 网关指标。

---

### Task 1：扩展 `ApplicationMetrics` 商品搜索指标门面

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`

- [ ] **Step 1：写失败测试，定义商品搜索指标语义**

在 `ApplicationMetricsTests` 末尾增加测试：

```java
@Test
void recordsProductSearchMetricsWithOnlyFixedCardinalityTags() {
    metrics.recordProductSearchEngine("ELASTICSEARCH", "success", Duration.ofMillis(12));
    metrics.recordProductSearchFallback("unavailable");
    metrics.recordProductSearchSyncConsume("retry", Duration.ofMillis(30));
    metrics.recordProductSearchSyncRetry("2");
    metrics.recordProductSearchRebuild("success", Duration.ofSeconds(3));
    metrics.recordProductSearchRebuildBulkFailures(4);
    metrics.recordProductSearchRebuildDocumentDrift(6);

    metrics.recordProductSearchEngine("keyword-user-controlled", "event-123", Duration.ZERO);
    metrics.recordProductSearchSyncConsume("event-123", Duration.ZERO);
    metrics.recordProductSearchRebuild("product_20260721000000", Duration.ZERO);

    assertThat(registry.get("app.product.search.engine.requests")
            .tags("engine", "elasticsearch", "outcome", "success").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.engine.duration")
            .tags("engine", "elasticsearch", "outcome", "success").timer().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.fallbacks")
            .tag("reason", "unavailable").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.sync.consume")
            .tag("outcome", "retry").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.sync.consume.duration")
            .tag("outcome", "retry").timer().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.sync.retries")
            .tag("stage", "2").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.rebuild.executions")
            .tag("outcome", "success").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.rebuild.duration")
            .tag("outcome", "success").timer().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.rebuild.bulk.failures").counter().count())
            .isEqualTo(4);
    assertThat(registry.get("app.product.search.rebuild.document.drift").summary().totalAmount())
            .isEqualTo(6);
    assertThat(registry.get("app.product.search.engine.requests")
            .tags("engine", "other", "outcome", "other").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.sync.consume")
            .tag("outcome", "other").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.rebuild.executions")
            .tag("outcome", "other").counter().count())
            .isEqualTo(1);
}
```

- [ ] **Step 2：运行测试确认失败**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ApplicationMetricsTests test
```

Expected: 编译失败，提示 `recordProductSearchEngine` 等方法不存在。

- [ ] **Step 3：实现商品搜索指标门面**

在 `ApplicationMetrics` 中新增白名单：

```java
private static final Set<String> PRODUCT_SEARCH_ENGINES = Set.of("elasticsearch", "mysql");
private static final Set<String> PRODUCT_SEARCH_ENGINE_OUTCOMES = Set.of(
        "success", "unavailable", "error");
private static final Set<String> PRODUCT_SEARCH_FALLBACK_REASONS = Set.of("unavailable");
private static final Set<String> PRODUCT_SEARCH_SYNC_OUTCOMES = Set.of(
        "success", "duplicate", "retry", "dlq", "error");
private static final Set<String> PRODUCT_SEARCH_SYNC_RETRY_STAGES = Set.of("1", "2", "3");
private static final Set<String> PRODUCT_SEARCH_REBUILD_OUTCOMES = Set.of("success", "error");
```

在 `recordAiTaskRetry` 后新增方法：

```java
public void recordProductSearchEngine(String engine, String outcome, Duration duration) {
    String safeEngine = bounded(normalize(engine), PRODUCT_SEARCH_ENGINES);
    String safeOutcome = bounded(normalize(outcome), PRODUCT_SEARCH_ENGINE_OUTCOMES);
    registry.counter(
            "app.product.search.engine.requests",
            "engine", safeEngine,
            "outcome", safeOutcome
    ).increment();
    registry.timer(
            "app.product.search.engine.duration",
            "engine", safeEngine,
            "outcome", safeOutcome
    ).record(nonNegative(duration));
}

public void recordProductSearchFallback(String reason) {
    registry.counter(
            "app.product.search.fallbacks",
            "reason", bounded(normalize(reason), PRODUCT_SEARCH_FALLBACK_REASONS)
    ).increment();
}

public void recordProductSearchSyncConsume(String outcome, Duration duration) {
    String safeOutcome = bounded(normalize(outcome), PRODUCT_SEARCH_SYNC_OUTCOMES);
    registry.counter("app.product.search.sync.consume", "outcome", safeOutcome).increment();
    registry.timer("app.product.search.sync.consume.duration", "outcome", safeOutcome)
            .record(nonNegative(duration));
}

public void recordProductSearchSyncRetry(String stage) {
    registry.counter(
            "app.product.search.sync.retries",
            "stage", bounded(stage, PRODUCT_SEARCH_SYNC_RETRY_STAGES)
    ).increment();
}

public void recordProductSearchRebuild(String outcome, Duration duration) {
    String safeOutcome = bounded(normalize(outcome), PRODUCT_SEARCH_REBUILD_OUTCOMES);
    registry.counter("app.product.search.rebuild.executions", "outcome", safeOutcome).increment();
    registry.timer("app.product.search.rebuild.duration", "outcome", safeOutcome)
            .record(nonNegative(duration));
}

public void recordProductSearchRebuildBulkFailures(long count) {
    long safeCount = Math.max(0L, count);
    if (safeCount > 0L) {
        registry.counter("app.product.search.rebuild.bulk.failures").increment(safeCount);
    }
}

public void recordProductSearchRebuildDocumentDrift(long drift) {
    long safeDrift = Math.max(0L, drift);
    if (safeDrift > 0L) {
        registry.summary("app.product.search.rebuild.document.drift").record(safeDrift);
    }
}
```

- [ ] **Step 4：运行测试确认通过**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ApplicationMetricsTests test
```

Expected: `ApplicationMetricsTests` 全部通过。

- [ ] **Step 5：提交**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java
git commit -m "功能：新增商品搜索可观测指标门面"
```

---

### Task 2：接入搜索网关耗时与降级指标

**Files:**
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchProductSearchGatewayTests.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/MySqlProductSearchGatewayTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchServiceTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchProductSearchGateway.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/MySqlProductSearchGateway.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchServiceConfiguration.java`

- [ ] **Step 1：写 ES 网关失败测试**

新增 `ElasticsearchProductSearchGatewayTests`，覆盖成功、可降级失败和非降级错误：

```java
@ExtendWith(MockitoExtension.class)
class ElasticsearchProductSearchGatewayTests {
    @Mock
    private ElasticsearchClient client;
    @Mock
    private ElasticsearchException elasticsearchException;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);
    private ElasticsearchProductSearchGateway gateway;

    @BeforeEach
    void setUp() {
        ElasticsearchSearchProperties properties = new ElasticsearchSearchProperties();
        properties.setIndexAlias("product_current");
        gateway = new ElasticsearchProductSearchGateway(client, properties, metrics);
    }

    @Test
    void recordsSuccessfulElasticsearchSearchLatency() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenReturn(responseWithHit("1001"));

        assertThat(gateway.search(new ProductSearchCriteria("通勤", null, 500))).containsExactly(1001L);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.engine.duration")
                .tags("engine", "elasticsearch", "outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsUnavailableWhenElasticsearchCanFallback() throws IOException {
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenThrow(new IOException("offline"));

        assertThatThrownBy(() -> gateway.search(new ProductSearchCriteria("通勤", null, 500)))
                .isInstanceOf(ProductSearchUnavailableException.class);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "unavailable").counter().count())
                .isEqualTo(1);
    }

    @Test
    void recordsErrorWhenElasticsearchFailureMustSurface() throws IOException {
        when(elasticsearchException.status()).thenReturn(400);
        when(client.search(any(SearchRequest.class), eq(Void.class))).thenThrow(elasticsearchException);

        assertThatThrownBy(() -> gateway.search(new ProductSearchCriteria("通勤", null, 500)))
                .isSameAs(elasticsearchException);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "elasticsearch", "outcome", "error").counter().count())
                .isEqualTo(1);
    }

    private SearchResponse<Void> responseWithHit(String id) {
        return SearchResponse.of(builder -> builder
                .took(1)
                .timedOut(false)
                .shards(shards -> shards.total(1).successful(1).failed(0))
                .hits(hits -> hits.hits(hit -> hit.index("product_current").id(id))));
    }
}
```

- [ ] **Step 2：写 MySQL 网关失败测试**

新增 `MySqlProductSearchGatewayTests`：

```java
@ExtendWith(MockitoExtension.class)
class MySqlProductSearchGatewayTests {
    @Mock
    private ProductMapper productMapper;

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

    @Test
    void recordsSuccessfulMysqlSearchLatency() {
        MySqlProductSearchGateway gateway = new MySqlProductSearchGateway(productMapper, metrics);
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(productMapper.searchProductIds("通勤", null, 500)).thenReturn(List.of(1001L));

        assertThat(gateway.search(criteria)).containsExactly(1001L);

        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "mysql", "outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("app.product.search.engine.duration")
                .tags("engine", "mysql", "outcome", "success").timer().count())
                .isEqualTo(1);
    }

    @Test
    void recordsMysqlSearchErrorAndRethrows() {
        MySqlProductSearchGateway gateway = new MySqlProductSearchGateway(productMapper, metrics);
        ProductSearchCriteria criteria = new ProductSearchCriteria("通勤", null, 500);
        when(productMapper.searchProductIds("通勤", null, 500))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertThatThrownBy(() -> gateway.search(criteria))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("db unavailable");
        assertThat(registry.get("app.product.search.engine.requests")
                .tags("engine", "mysql", "outcome", "error").counter().count())
                .isEqualTo(1);
    }
}
```

- [ ] **Step 3：更新搜索服务失败测试**

在 `ProductSearchServiceTests` 中新增 registry 和 metrics 字段，`setUp` 改为：

```java
private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

@BeforeEach
void setUp() {
    service = new ProductSearchService(primaryGateway, fallbackGateway, productMapper, 500, metrics);
}
```

在 `fallsBackOnlyWhenPrimaryIsUnavailable` 末尾增加断言：

```java
assertThat(registry.get("app.product.search.fallbacks")
        .tag("reason", "unavailable").counter().count())
        .isEqualTo(1);
```

在 `propagatesProgrammingErrorsWithoutHidingThemBehindFallback` 末尾增加断言：

```java
assertThat(registry.find("app.product.search.fallbacks").counter()).isNull();
```

- [ ] **Step 4：运行测试确认失败**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ElasticsearchProductSearchGatewayTests,MySqlProductSearchGatewayTests,ProductSearchServiceTests test
```

Expected: 编译失败，提示网关和搜索服务构造器不匹配，或指标未记录。

- [ ] **Step 5：实现网关和降级指标**

`ElasticsearchProductSearchGateway` 增加字段和构造器参数：

```java
private final ApplicationMetrics metrics;

public ElasticsearchProductSearchGateway(
        ElasticsearchClient client,
        ElasticsearchSearchProperties properties,
        ApplicationMetrics metrics
) {
    this.client = client;
    this.properties = properties;
    this.metrics = metrics;
}
```

用 `System.nanoTime()` 包裹 `search`：

```java
long startedNanos = System.nanoTime();
SearchRequest request = buildRequest(criteria);
try {
    SearchResponse<Void> response = client.search(request, Void.class);
    metrics.recordProductSearchEngine("elasticsearch", "success", elapsed(startedNanos));
    return response.hits().hits().stream().map(hit -> parseSpuId(hit.id())).toList();
} catch (IOException exception) {
    metrics.recordProductSearchEngine("elasticsearch", "unavailable", elapsed(startedNanos));
    throw new ProductSearchUnavailableException("Elasticsearch 连接不可用", exception);
} catch (ElasticsearchException exception) {
    if (exception.status() == 404 || exception.status() >= 500) {
        metrics.recordProductSearchEngine("elasticsearch", "unavailable", elapsed(startedNanos));
        throw new ProductSearchUnavailableException("Elasticsearch 查询暂时不可用", exception);
    }
    metrics.recordProductSearchEngine("elasticsearch", "error", elapsed(startedNanos));
    throw exception;
} catch (RuntimeException exception) {
    metrics.recordProductSearchEngine("elasticsearch", "error", elapsed(startedNanos));
    throw exception;
}
```

同类中新增私有方法：

```java
private Duration elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
}
```

`MySqlProductSearchGateway` 同样增加 `ApplicationMetrics` 并包裹查询：

```java
long startedNanos = System.nanoTime();
try {
    List<Long> result = productMapper.searchProductIds(
            criteria.keyword(), criteria.category(), criteria.limit());
    metrics.recordProductSearchEngine("mysql", "success", elapsed(startedNanos));
    return result;
} catch (RuntimeException exception) {
    metrics.recordProductSearchEngine("mysql", "error", elapsed(startedNanos));
    throw exception;
}
```

`ProductSearchService` 增加 `ApplicationMetrics` 字段，并在捕获 `ProductSearchUnavailableException` 后记录：

```java
metrics.recordProductSearchFallback("unavailable");
orderedSpuIds = fallbackGateway.search(criteria);
```

`ProductSearchServiceConfiguration` 的 bean 方法增加参数：

```java
ApplicationMetrics metrics
```

并返回：

```java
return new ProductSearchService(
        primaryGateway, mysqlGateway, productMapper, properties.getSearchLimit(), metrics);
```

- [ ] **Step 6：运行测试确认通过**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ElasticsearchProductSearchGatewayTests,MySqlProductSearchGatewayTests,ProductSearchServiceTests test
```

Expected: 三组测试通过。

- [ ] **Step 7：提交**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchProductSearchGateway.java `
        backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/MySqlProductSearchGateway.java `
        backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchService.java `
        backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchServiceConfiguration.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchProductSearchGatewayTests.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/MySqlProductSearchGatewayTests.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchServiceTests.java
git commit -m "功能：记录商品搜索耗时与降级指标"
```

---

### Task 3：接入 Product Search Worker 同步指标

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java`

- [ ] **Step 1：写失败测试，覆盖 Worker 成功与重复分支**

在 `ProductSearchWorkerTests` 增加 registry / metrics，`setUp` 改为：

```java
private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
private final ApplicationMetrics metrics = new ApplicationMetrics(registry);

@BeforeEach
void setUp() {
    worker = new ProductSearchWorker(projector, inboxMapper, consumptionRecorder, rabbitTemplate, metrics);
}
```

在 `projectsAndRecordsInboxBeforeAcknowledging` 末尾增加：

```java
assertThat(registry.get("app.product.search.sync.consume")
        .tag("outcome", "success").counter().count()).isEqualTo(1);
assertThat(registry.get("app.product.search.sync.consume.duration")
        .tag("outcome", "success").timer().count()).isEqualTo(1);
```

在 `duplicateEventIsAcknowledgedWithoutProjection` 和 `concurrentDuplicateIsAcknowledgedWithoutRetry` 末尾增加：

```java
assertThat(registry.get("app.product.search.sync.consume")
        .tag("outcome", "duplicate").counter().count()).isEqualTo(1);
```

- [ ] **Step 2：写失败测试，覆盖 retry、DLQ 和发布失败**

在 `inboxLookupFailureUsesRetryQueue` 末尾增加：

```java
assertThat(registry.get("app.product.search.sync.consume")
        .tag("outcome", "retry").counter().count()).isEqualTo(1);
assertThat(registry.get("app.product.search.sync.retries")
        .tag("stage", "1").counter().count()).isEqualTo(1);
```

在 `inboxLookupFailureAtLastStageGoesToDeadLetterQueue`、`invalidMessageGoesDirectlyToDeadLetterQueue` 和 `permanentElasticsearchFailureGoesDirectlyToDeadLetterQueue` 末尾增加：

```java
assertThat(registry.get("app.product.search.sync.consume")
        .tag("outcome", "dlq").counter().count()).isEqualTo(1);
```

新增发布失败测试，确认不会 ACK 并记录 `error`：

```java
@Test
void retryPublishFailureRecordsErrorAndDoesNotAcknowledge() throws IOException {
    doThrow(new ProductSearchUnavailableException("ES unavailable", new IOException()))
            .when(projector).project(1001L);
    doThrow(new AmqpException("rabbit unavailable"))
            .when(rabbitTemplate).send(eq(RabbitProductSearchTopology.EXCHANGE),
                    eq(RabbitProductSearchTopology.RETRY_10S_ROUTING_KEY), any(Message.class));

    assertThatThrownBy(() -> worker.handle(validPayload(), 0, 16L, channel))
            .isInstanceOf(AmqpException.class)
            .hasMessage("rabbit unavailable");

    verify(channel, never()).basicAck(16L, false);
    assertThat(registry.get("app.product.search.sync.consume")
            .tag("outcome", "error").counter().count()).isEqualTo(1);
}
```

- [ ] **Step 3：运行测试确认失败**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ProductSearchWorkerTests test
```

Expected: 编译失败，提示 `ProductSearchWorker` 构造器缺少 `ApplicationMetrics`，或指标断言找不到 meter。

- [ ] **Step 4：实现 Worker 指标**

`ProductSearchWorker` 增加字段：

```java
private final ApplicationMetrics metrics;
```

构造器增加参数并赋值：

```java
public ProductSearchWorker(
        ProductSearchIncrementalProjector projector,
        ProductSearchInboxMapper inboxMapper,
        ProductSearchConsumptionRecorder consumptionRecorder,
        RabbitTemplate rabbitTemplate,
        ApplicationMetrics metrics) {
    this.projector = projector;
    this.inboxMapper = inboxMapper;
    this.consumptionRecorder = consumptionRecorder;
    this.rabbitTemplate = rabbitTemplate;
    this.metrics = metrics;
}
```

重写 `handle` 的结果记录结构：

```java
public void handle(String payload, int retryStage, long deliveryTag, Channel channel) throws IOException {
    long startedNanos = System.nanoTime();
    String outcome = "error";
    try {
        ProductSearchSyncMessage message;
        try {
            message = parseAndValidate(payload);
        } catch (IllegalArgumentException exception) {
            publish(payload, RabbitProductSearchTopology.DLQ_ROUTING_KEY, retryStage);
            channel.basicAck(deliveryTag, false);
            outcome = "dlq";
            return;
        }

        try {
            if (inboxMapper.exists(ProductSearchConsumptionRecorder.CONSUMER_NAME, message.eventId())) {
                channel.basicAck(deliveryTag, false);
                outcome = "duplicate";
                return;
            }
            projector.project(message.spuId());
            try {
                consumptionRecorder.record(message);
            } catch (DuplicateKeyException duplicate) {
                channel.basicAck(deliveryTag, false);
                outcome = "duplicate";
                return;
            }
            channel.basicAck(deliveryTag, false);
            outcome = "success";
        } catch (RuntimeException exception) {
            outcome = routeFailure(payload, retryStage, exception);
            channel.basicAck(deliveryTag, false);
        }
    } finally {
        metrics.recordProductSearchSyncConsume(outcome, elapsed(startedNanos));
    }
}
```

让 `routeFailure` 返回最终路由动作并在重试成功发布后记录 stage：

```java
private String routeFailure(String payload, int retryStage, RuntimeException failure) {
    if (isPermanent(failure) || retryStage >= 3) {
        publish(payload, RabbitProductSearchTopology.DLQ_ROUTING_KEY, retryStage);
        return "dlq";
    }
    int nextStage = retryStage + 1;
    publish(payload, retryRoutingKey(nextStage), nextStage);
    metrics.recordProductSearchSyncRetry(String.valueOf(nextStage));
    return "retry";
}
```

同类增加：

```java
private Duration elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
}
```

- [ ] **Step 5：运行测试确认通过**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ProductSearchWorkerTests test
```

Expected: `ProductSearchWorkerTests` 通过。

- [ ] **Step 6：提交**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/sync/ProductSearchWorker.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchWorkerTests.java
git commit -m "功能：记录商品搜索同步消费指标"
```

---

### Task 4：接入全量重建耗时、Bulk 失败和文档差异指标

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`

- [ ] **Step 1：写失败测试，覆盖重建成功和失败耗时**

在 `ProductSearchIndexServiceTests` 增加 registry / metrics 字段：

```java
private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
private final ApplicationMetrics metrics = new ApplicationMetrics(registry);
```

`setUp` 中服务构造器改为：

```java
service = new ProductSearchIndexService(
        client, productMapper, properties, lifecycleService, cacheVersionService, metrics);
```

所有带 `Optional.of(rebuildCompensator)` 的构造器调用改为：

```java
service = new ProductSearchIndexService(
        client, productMapper, properties(), lifecycleService,
        Optional.of(rebuildCompensator), cacheVersionService, metrics);
```

在 `returnsSuccessWhenPostSwitchRetentionCleanupFails` 末尾增加：

```java
assertThat(registry.get("app.product.search.rebuild.executions")
        .tag("outcome", "success").counter().count()).isEqualTo(1);
assertThat(registry.get("app.product.search.rebuild.duration")
        .tag("outcome", "success").timer().count()).isEqualTo(1);
```

在 `deletesNewIndexWhenBulkWriteFails` 末尾增加：

```java
assertThat(registry.get("app.product.search.rebuild.executions")
        .tag("outcome", "error").counter().count()).isEqualTo(1);
```

- [ ] **Step 2：写失败测试，覆盖 Bulk item 失败和文档 drift**

新增 Bulk item 失败测试：

```java
@Test
void recordsBulkItemFailuresBeforeFailingRebuild() throws IOException {
    prepareRowsAndCreatedIndex();
    BulkResponse bulkResponse = mock(BulkResponse.class);
    BulkResponseItem failedItem = mock(BulkResponseItem.class);
    ErrorCause error = mock(ErrorCause.class);
    when(error.reason()).thenReturn("mapping rejected");
    when(failedItem.error()).thenReturn(error);
    when(bulkResponse.errors()).thenReturn(true);
    when(bulkResponse.items()).thenReturn(List.of(failedItem));
    when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

    assertThatThrownBy(service::rebuild)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("商品索引批量写入失败");

    assertThat(registry.get("app.product.search.rebuild.bulk.failures").counter().count())
            .isEqualTo(1);
    assertThat(registry.get("app.product.search.rebuild.executions")
            .tag("outcome", "error").counter().count()).isEqualTo(1);
}
```

新增文档数量差异测试：

```java
@Test
void recordsDocumentDriftBeforeFailingRebuild() throws IOException {
    prepareRowsAndCreatedIndex();
    BulkResponse bulkResponse = mock(BulkResponse.class);
    when(bulkResponse.errors()).thenReturn(false);
    when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);
    when(client.count(any(java.util.function.Function.class)))
            .thenReturn(CountResponse.of(builder -> builder.count(3).shards(shards -> shards
                    .failed(0).successful(1).total(1))));

    assertThatThrownBy(service::rebuild)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("商品索引文档数量不一致");

    assertThat(registry.get("app.product.search.rebuild.document.drift").summary().totalAmount())
            .isEqualTo(2);
}
```

- [ ] **Step 3：运行测试确认失败**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ProductSearchIndexServiceTests test
```

Expected: 编译失败，提示构造器不匹配，或指标断言找不到 meter。

- [ ] **Step 4：实现重建指标**

`ProductSearchIndexService` 增加字段：

```java
private final ApplicationMetrics metrics;
```

所有构造器增加 `ApplicationMetrics metrics` 并传到最终私有构造器。`rebuild` 顶部改为：

```java
long startedNanos = System.nanoTime();
String outcome = "error";
if (!rebuilding.compareAndSet(false, true)) {
    metrics.recordProductSearchRebuild(outcome, elapsed(startedNanos));
    throw new BadRequestException("商品搜索索引正在重建，请勿重复提交");
}
```

成功返回前设置 outcome：

```java
ProductSearchRebuildResult result =
        new ProductSearchRebuildResult(indexName, actualCount, properties.getIndexAlias());
outcome = "success";
return result;
```

`finally` 改为：

```java
} finally {
    metrics.recordProductSearchRebuild(outcome, elapsed(startedNanos));
    rebuilding.set(false);
}
```

`bulkIndex` 中 `response.errors()` 分支先统计失败 item 数：

```java
long failedItems = response.items().stream()
        .filter(item -> item.error() != null)
        .count();
metrics.recordProductSearchRebuildBulkFailures(failedItems);
```

文档数量校验处先记录 drift：

```java
if (actualCount != rows.size()) {
    metrics.recordProductSearchRebuildDocumentDrift(Math.abs(actualCount - rows.size()));
    throw new IllegalStateException(
            "商品索引文档数量不一致，预期 " + rows.size() + "，实际 " + actualCount);
}
```

同类增加：

```java
private Duration elapsed(long startedNanos) {
    return Duration.ofNanos(System.nanoTime() - startedNanos);
}
```

- [ ] **Step 5：运行测试确认通过**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ProductSearchIndexServiceTests test
```

Expected: `ProductSearchIndexServiceTests` 通过。

- [ ] **Step 6：提交**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java `
        backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java
git commit -m "功能：记录商品搜索重建指标"
```

---

### Task 5：文档、回归验证和收尾

**Files:**
- Modify: `docs/elasticsearch/README.md`
- Modify: `docs/elasticsearch/development-backlog.md`

- [ ] **Step 1：更新 Elasticsearch README**

在 `docs/elasticsearch/README.md` 的 P3 排障段落后增加“商品搜索可观测性（P4）”：

````markdown
## 商品搜索可观测性（P4）

后端已经通过现有 `/actuator/prometheus` 暴露商品搜索指标。启动 Web 或 Worker 后可以查看：

```powershell
Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/prometheus |
    Select-String 'app_product_search'
```

核心指标包括：

- `app_product_search_engine_requests_total`：ES / MySQL 搜索请求次数，按 `engine` 和 `outcome` 区分。
- `app_product_search_engine_duration_seconds`：ES / MySQL 搜索耗时。
- `app_product_search_fallbacks_total`：ES 不可用后真实回退 MySQL 的次数。
- `app_product_search_sync_consume_total`：商品搜索 Worker 本次消费结果。
- `app_product_search_sync_retries_total`：进入 10 / 60 / 300 秒重试队列的阶段计数。
- `app_product_search_rebuild_executions_total`：全量重建成功 / 失败次数。
- `app_product_search_rebuild_duration_seconds`：全量重建耗时。
- `app_product_search_rebuild_bulk_failures_total`：Bulk item 失败数量。
- `app_product_search_rebuild_document_drift`：MySQL 快照数量与 ES count 的差异值。

这些指标不会把关键词、分类、SPU ID、eventId、索引名或 requestId 放进标签。RabbitMQ 队列深度仍由 RabbitMQ Prometheus 插件暴露，不在 Java 应用内重复采集。
````

- [ ] **Step 2：更新待开发清单**

把 `docs/elasticsearch/development-backlog.md` 的 P4 状态改为“已完成”，并在文末新增验收记录：

```markdown
## P4 已交付范围

- 搜索网关记录 ES / MySQL 查询耗时和 success、unavailable、error 结果；
- 搜索编排层只在真实 ES 不可用并回退 MySQL 时记录 fallback；
- Worker 记录 success、duplicate、retry、dlq、error，并记录重试阶段；
- 全量重建记录 success / error 耗时、Bulk item 失败数量和文档数量差异；
- 所有商品搜索指标通过现有 `/actuator/prometheus` 暴露，标签不包含关键词、SPU ID、eventId、索引名或 requestId。

## P4 验证记录

- 单元测试覆盖指标白名单、ES / MySQL 搜索耗时、降级、Worker 重试 / DLQ、Bulk 失败和文档数量差异；
- `backend\mvnw.cmd verify`、`docker compose config --quiet` 和 `git diff --check` 已通过；
- Dashboard 与告警规则未在本轮实现，后续基于这些稳定指标单独设计。
```

- [ ] **Step 3：运行聚焦回归**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ApplicationMetricsTests,ElasticsearchProductSearchGatewayTests,MySqlProductSearchGatewayTests,ProductSearchServiceTests,ProductSearchWorkerTests,ProductSearchIndexServiceTests test
```

Expected: 聚焦测试全部通过。

- [ ] **Step 4：运行完整验证**

Run:

```powershell
cd backend
.\mvnw.cmd verify
cd ..
docker compose config --quiet
git diff --check
```

Expected:

- Maven `BUILD SUCCESS`；
- Checkstyle 无违规；
- Compose 配置有效；
- `git diff --check` 无空白错误。

- [ ] **Step 5：提交文档和验证结果**

```powershell
git add docs/elasticsearch/README.md docs/elasticsearch/development-backlog.md
git commit -m "文档：更新商品搜索可观测性验收"
```

---

## 自检清单

- 每个新增生产代码入口都有先失败的测试。
- 指标标签只使用固定白名单，动态值统一收敛为 `other`。
- 指标写入不改变搜索、同步和重建的原有业务分支。
- Worker 发布重试 / DLQ 失败时不 ACK，并记录 `error`。
- 全量重建成功后历史清理失败仍保持成功结果。
- 文档说明如何在本地通过 Prometheus 端点查看指标。
