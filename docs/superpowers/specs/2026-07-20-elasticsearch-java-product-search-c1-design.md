# Elasticsearch Java 商品搜索 C1 设计

日期：2026-07-20

状态：待用户审阅

## 1. 目标

在方案 B 已完成的 Elasticsearch 9.4.3、Kibana、SmartCN、`product_current` 别名和搜索实验基础上，让现有 Java 商品搜索真正使用 Elasticsearch，同时保持 MySQL 的事实源地位和现有 HTTP 契约。

本阶段形成以下最小闭环：

```text
MySQL 商品事实
  -> internal 全量重建
  -> 新建版本化 Elasticsearch 索引
  -> Bulk 写入并原子切换 product_current
  -> Java 根据关键词搜索有序 SPU ID
  -> MySQL 批量补齐当前商品摘要
  -> Elasticsearch 不可用时回退 MySQL LIKE
```

## 2. 范围

### 2.1 包含

- 引入官方 `elasticsearch-java:9.4.3` 客户端；
- 增加可关闭的 Elasticsearch 配置与连接模块；
- 建立商品搜索模块接口和 Elasticsearch/MySQL 两个适配器；
- 保持 `GET /api/products` 和 `GET /internal/products/search` 请求、响应结构不变；
- Elasticsearch 返回相关度排序后的 SPU ID；
- MySQL 批量补齐名称、图片、版型和最新价格区间；
- 补数后保持 Elasticsearch 顺序，并剔除已经下架或不存在的商品；
- 增加 `POST /internal/search/products/rebuild` 全量重建入口；
- 每次重建创建新物理索引并原子切换 `product_current`；
- Elasticsearch 关闭、未初始化、超时或服务端不可用时回退现有 MySQL LIKE；
- 保留现有 Redis 商品搜索结果缓存；
- 增加单元测试、Mapper 测试、Controller 测试和真实 Elasticsearch 手动验收；
- 所有新增 Java 顶层类型、核心方法和复杂 SQL 按项目规范写明职责、事实边界、排序与降级原因。

### 2.2 不包含

- 不做商品新增、修改、上下架后的实时或准实时增量同步；
- 不做 RabbitMQ、Outbox、CDC、Canal、重试队列或死信队列；
- 不把价格和库存作为 Elasticsearch 最终事实返回；
- 不改推荐候选查询和 Python AI 契约；
- 不改前端请求参数和响应类型；
- 不做搜索分页、搜索建议、拼音、纠错、同义词、IK、自定义词典；
- 不做 RAG 或向量检索；
- 不在应用 readiness 中强制要求 Elasticsearch 健康，因为 MySQL 降级路径允许商城继续提供搜索。

## 3. 模块与接口

### 3.1 搜索模块 seam

在 `product/search` 包建立一个真实 seam：

```java
public interface ProductSearchGateway {
    List<Long> search(ProductSearchCriteria criteria);
}
```

两个适配器实现同一接口：

- `ElasticsearchProductSearchGateway`：使用全文检索、字段权重和 keyword 过滤，返回有序 SPU ID；
- `MySqlProductSearchGateway`：使用现有 MySQL LIKE 语义返回有序 SPU ID，承担关闭开关和异常降级。

接口只暴露调用方真正需要的“有序商品身份”，不泄露 Elasticsearch Hit、`_score`、Java Client 类型或 MyBatis模型。相关度只负责决定顺序，当前商品事实统一由 MySQL 补齐。

### 3.2 搜索编排模块

`ProductSearchService` 对调用方提供一个深接口：

```java
List<ProductSearchItem> search(String keyword, String category)
```

内部完成：

1. 标准化关键词和类目；
2. Elasticsearch 开启时调用主适配器；
3. 识别允许降级的失败；
4. 调用 MySQL 适配器回退；
5. 根据有序 ID 批量读取 MySQL 摘要；
6. 按搜索顺序重新组装并过滤失效商品。

`ProductCatalogService.searchProducts` 保留现有 Redis Cache Aside，只把缓存未命中后的搜索实现委托给 `ProductSearchService`。这样 Controller、缓存键和返回 DTO 均保持稳定。

### 3.3 重建模块

`ProductSearchIndexService` 提供：

```java
ProductSearchRebuildResult rebuild()
```

该接口隐藏 MySQL 源数据读取、版本化索引命名、Mapping 加载、Bulk 分批写入、文档数量校验和 alias 切换。

重建入口为：

```http
POST /internal/search/products/rebuild
X-Internal-Token: <token>
```

沿用现有 `/internal/**` 鉴权，不新增公开管理入口。

## 4. 数据模型

### 4.1 搜索条件

`ProductSearchCriteria` 包含：

- `keyword`：去除首尾空白后的可空关键词；
- `category`：去除首尾空白后的可空精确类目；
- `limit`：C1 固定最大 500，防止无分页接口一次拉取无限结果。

当前约 40 个 SPU，500 的上限不改变现有演示行为。正式商品量接近该上限前必须先设计分页，不通过继续提高上限掩盖契约问题。

### 4.2 搜索文档

`ProductSearchDocument` 与方案 B Mapping 对齐：

```text
spuId, spuCode, name, description, category, fitType,
materials, styles, scenes, seasons, status, updatedAt
```

其中：

- 商品、类目、版型、材质、季节、风格和状态来自 MySQL；
- `scenes` 从 `product_attribute` 中 `attr_name IN ('场景', '适用场景')` 的值确定性生成；
- `updatedAt` 在 C1 使用本次重建时间，因为当前商品表没有统一、可信的更新时间列；
- 不写 SKU 库存、订单、支付或用户隐私；
- 价格不写入第一版文档，避免搜索副本被误当作交易事实。

### 4.3 MySQL 补数

新增批量查询：

```java
List<ProductSearchItem> findSearchItemsBySpuIds(List<Long> spuIds);
```

SQL 只返回当前 `on_sale` 商品，并实时聚合当前在售 SKU 的最低价和最高价。SQL 不负责保持 `IN (...)` 入参顺序；Java 使用 `spuId -> ProductSearchItem` 映射按 Elasticsearch ID 顺序重排。

## 5. Elasticsearch 查询

### 5.1 有关键词

使用与方案 B 一致的 SmartCN 多字段权重：

```text
name.smartcn^5
styles.search^3
category.search^2
scenes.search^2
materials.search^1.5
description.smartcn
```

过滤：

```text
status = on_sale
category = 请求类目（提供时）
```

按 `_score` 降序，`spuId` 升序兜底，保证同分结果稳定。

### 5.2 无关键词

使用 `match_all`，继续应用 `status/category` 过滤，并按 `spuId` 升序。这保留当前浏览页“空关键词列出商品”的行为，不把随机相关度引入普通浏览。

### 5.3 返回值

Elasticsearch 适配器只返回 SPU ID，不直接返回名称、图片、价格或库存。高亮能力已在方案 B 学习验证，但现有 `ProductSearchItem` 没有高亮字段，C1 不修改 HTTP 契约；高亮接入留给后续单独设计。

## 6. 全量重建与别名切换

### 6.1 Mapping 来源

把运行时 Mapping 放到：

```text
backend/src/main/resources/elasticsearch/product-index.json
```

方案 B 的命令和文档改为引用同一份运行时 Mapping，避免 `docs` 和 Java 资源各维护一份产生漂移。

### 6.2 重建步骤

```text
读取全部 MySQL 搜索文档
  -> 创建 product_<UTC时间戳> 新索引
  -> 按固定批次 Bulk 写入
  -> refresh
  -> 校验索引 count 等于源文档数
  -> 单次 update aliases：移除旧指向并添加新写索引
  -> 返回新索引名、文档数、耗时
```

在别名切换前出现任何错误：

- 删除本次未完成的新索引；
- 保持 `product_current` 仍指向旧索引；
- 返回失败，不影响当前搜索。

别名切换成功后不自动删除旧索引。本阶段保留旧索引用于人工回滚；旧索引清理策略以后单独设计。

### 6.3 并发重建

C1 在单个 Java 实例内使用互斥锁禁止两个重建同时执行。第二个请求返回明确的冲突错误，不排队。多实例分布式锁不在本阶段实现；进入多实例部署前必须补充分布式互斥或独立索引任务执行器。

## 7. 配置

新增配置前缀：

```properties
app.elasticsearch.enabled=${APP_ELASTICSEARCH_ENABLED:false}
app.elasticsearch.uris=${APP_ELASTICSEARCH_URIS:http://localhost:9200}
app.elasticsearch.index-alias=${APP_ELASTICSEARCH_INDEX_ALIAS:product_current}
app.elasticsearch.index-prefix=${APP_ELASTICSEARCH_INDEX_PREFIX:product_}
app.elasticsearch.connect-timeout-ms=1000
app.elasticsearch.socket-timeout-ms=2000
app.elasticsearch.search-limit=500
app.elasticsearch.bulk-batch-size=200
```

默认关闭的原因是：现有测试、只运行 MySQL 的开发环境和部署环境不能因为尚未配置 Elasticsearch 而启动失败。开启后客户端和 Elasticsearch 适配器才注册；关闭时搜索直接使用 MySQL 适配器，重建入口返回“功能未启用”。

本地验证通过设置：

```text
APP_ELASTICSEARCH_ENABLED=true
```

Elasticsearch 不加入 readiness 必选项，避免可降级依赖阻断整个商城。

## 8. 失败与降级语义

允许回退 MySQL 的情况：

- Elasticsearch 连接失败或超时；
- `product_current` 尚未创建；
- Elasticsearch 返回 5xx；
- 节点暂时不可用。

不静默降级的情况：

- Java 构造了非法查询；
- Mapping 与代码字段不一致导致 400；
- 反序列化或内部编程错误。

这些错误应暴露并修复，不能用 MySQL 回退长期掩盖。降级时写结构化 WARN，包含关键词是否为空、类目、异常类型和 fallback 结果数量，但不记录用户隐私或完整敏感请求。

全量重建不做 MySQL 降级：失败即返回失败，并保持旧 alias 不变。

## 9. Redis 语义

保留当前 `product:search:<keyword>:<category>` 缓存与 5～6 分钟 TTL。缓存命中时不会访问 Elasticsearch 或 MySQL。

C1 不新增 Redis wildcard 删除。全量重建切换 alias 后，已有搜索缓存可能在剩余 TTL 内继续返回旧列表；这是本阶段明确接受的最终一致性窗口。商品详情、价格和库存相关交易校验仍走 MySQL，不依赖该列表缓存。

如果后续要求切换后立即生效，应引入搜索缓存命名空间版本，而不是在生产 Redis 上使用 `KEYS product:search:*`。

## 10. 测试

### 10.1 单元测试

- Elasticsearch 开启时优先使用 Elasticsearch 适配器；
- 允许降级的异常触发 MySQL 适配器；
- 非降级异常继续抛出；
- MySQL 补数结果按 Elasticsearch ID 顺序恢复；
- MySQL 中不存在或已下架的 SPU 被剔除；
- 空关键词保持稳定浏览顺序；
- 重建并发请求被拒绝；
- Bulk 或 count 校验失败时不切换 alias；
- alias 切换成功时返回索引名和文档数。

### 10.2 Mapper 测试

- 能生成约 40 个真实搜索文档；
- 材质、季节、风格和场景聚合正确；
- 批量补数只返回在售商品和在售 SKU 价格；
- 补数入参为空时不生成非法 `IN ()`。

### 10.3 Controller 测试

- `/internal/search/products/rebuild` 仍受 internal token 保护；
- 开启时返回重建结果；
- 关闭时返回明确错误；
- 现有公开和 internal 商品搜索响应契约不变。

### 10.4 真实环境验收

在 Compose Elasticsearch 运行时：

1. 启动 Java 并开启开关；
2. 调用 rebuild；
3. 验证 alias 指向新索引且 count 与 MySQL 一致；
4. 调用“冬季通勤外套”并检查相关度顺序；
5. 停止 Elasticsearch；
6. 再次调用同一搜索并确认 MySQL 回退可用；
7. 恢复 Elasticsearch；
8. 执行 `mvnw.cmd verify`。

## 11. 文档与注释

- 更新 `docs/elasticsearch/README.md`，增加 Java 开关、重建和故障降级验证；
- 更新后端功能对照文档，区分“已搭实验环境”和“Java 搜索已接入”；
- 新增顶层 Java 类型必须有解释职责和事实边界的 Javadoc；
- 搜索编排、顺序恢复、alias 原子切换和失败清理必须注释“为什么这样设计”；
- MyBatis 复杂聚合 SQL 必须说明搜索副本来源和实时事实补数职责；
- 不写重复类名、方法名或赋值动作的无信息注释。

## 12. 完成标准

- Elasticsearch 开启时，两个现有商品搜索入口使用相关度搜索；
- Elasticsearch 返回的只是有序 SPU ID，最终商品摘要来自 MySQL；
- Elasticsearch 停止后搜索仍可通过 MySQL 返回；
- rebuild 使用新物理索引和原子 alias 切换，不删除旧索引；
- Java 关闭开关时项目行为与接入前一致；
- 所有新增 Java 类型和复杂逻辑符合注释规范；
- Maven `verify` 通过；
- 方案 B 的 Compose、SmartCN、索引和实验文档继续可用；
- 不包含 MQ、Outbox、增量同步或 RAG 变更。
