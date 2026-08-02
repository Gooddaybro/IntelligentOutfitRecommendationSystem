# Elasticsearch 商品搜索实验环境设计

日期：2026-07-20
状态：已确认，待实施

## 1. 背景

当前商城商品搜索由 Java 后端通过 MyBatis 查询 MySQL 实现：

```text
GET /api/products
  -> ProductCatalogService
  -> Redis 商品搜索缓存
  -> ProductMapper
  -> MySQL LIKE 查询
```

当前演示数据约有 40 个 SPU，MySQL 足以承担现有查询压力。本轮引入 Elasticsearch 的目的不是解决性能瓶颈，也不是立即替换线上搜索，而是独立学习并验证商品全文检索的工程能力：中文分词、倒排索引、多字段检索、精确过滤、字段权重、相关度、高亮、索引版本和别名。

为避免同时处理 Docker、Java Client、MySQL 同步、缓存和降级，本项目将 Elasticsearch 工作拆成两轮：

1. **方案 B（本设计）**：搭建 Elasticsearch 与 Kibana，建立商品实验索引，手工写入真实项目商品并完成搜索实验。
2. **方案 C（后续设计）**：接入 Java，增加 MySQL 全量重建、真实商品搜索、Redis 缓存和 MySQL 降级。

## 2. 本轮目标

本轮需要证明以下链路成立：

```text
Elasticsearch 能稳定运行
  -> 商品文档能够建立索引
  -> standard 与 SmartCN 的中文分词差异可观察
  -> 中文关键词能够检索真实项目商品
  -> 字段权重、精确过滤、高亮和相关度解释可验证
```

本轮完成后，开发者应当能够解释：

- `text` 与 `keyword` 的职责差异；
- analyzer 如何影响写入和查询；
- 倒排索引与 MySQL `LIKE '%keyword%'` 的区别；
- `match`、`multi_match`、`term` 和 `filter` 的使用边界；
- boost 如何影响相关度，而不是直接把 `_score` 设置为固定值；
- 物理索引与业务别名为什么需要分离。

## 3. 范围

### 3.1 本轮包含

- 在现有 Docker Compose 中新增单节点 Elasticsearch；
- 新增同版本 Kibana；
- 为 Elasticsearch 配置持久化数据卷、健康检查和明确的内存限制；
- 安装并验证 SmartCN 官方核心分析插件；
- 创建物理索引 `product_v1`；
- 创建业务别名 `product_current` 并将 `product_v1` 标记为写索引；
- 从当前 Flyway 演示数据中选取 10 件真实商品，转换成实验文档并手工批量写入；
- 在 Kibana Dev Tools 中完成分词、查询、过滤、高亮、排序和 `_explain` 实验；
- 保存可重复执行的索引创建、数据写入和查询示例；
- 记录启动方式、验证步骤、清理方式和常见故障。

### 3.2 本轮不包含

- 不添加 Elasticsearch Java API Client；
- 不创建 `ProductSearchGateway`、`SearchService` 或 Java 搜索实现；
- 不修改 `ProductController`、`ProductCatalogService`、`ProductMapper`；
- 不修改前端商品搜索；
- 不从 MySQL 自动全量同步商品；
- 不增加 `/internal/search/products/rebuild`；
- 不增加 Redis 搜索缓存；
- 不实现 Elasticsearch 故障时回退 MySQL；
- 不实现商品增量同步、RabbitMQ、Outbox、CDC 或 Canal；
- 不把 Elasticsearch 用于 Python RAG 或向量检索；
- 不搭建多节点集群、Logstash、Beats 或生产监控；
- 不顺带在 Docker Compose 中补建 RabbitMQ。当前 Compose 虽然对应的 Java 工程已有 RabbitMQ 依赖和配置，但尚无 RabbitMQ 服务，本轮保持这一现状。

## 4. 核心设计原则

### 4.1 学习环境与业务链路隔离

Elasticsearch 和 Kibana可以与现有依赖共同启动，但本轮没有任何运行时代码依赖 Elasticsearch。Elasticsearch 启动失败时，现有 MySQL 商品搜索仍应正常工作。

### 4.2 使用真实项目商品，避免一次性样例

实验文档来自现有 Flyway 种子数据，而不是另造一套皮夹克等虚构商品。这样本轮形成的查询用例可以在下一轮转化为 Java 接入后的回归用例。

### 4.3 同一份数据对比分词器

分词对比不采用先建普通索引、再修改 analyzer 的方式。`product_v1` 从创建时就在全文字段上提供 standard 和 SmartCN 两套字段，使相同数据、相同查询、相同权重下唯一变量只有 analyzer。

### 4.4 索引版本与业务名称分离

应用或实验查询使用 `product_current`，不直接依赖 `product_v1`。未来 Mapping 变更时可以新建 `product_v2`、重新写入数据并切换别名，无需让调用方修改索引名称。

### 4.5 搜索文档不是交易事实源

即使后续将价格写入搜索索引，它也只能用于粗筛或展示快照。MySQL 继续作为商品、SKU、价格、库存、上下架状态、订单和支付的唯一事实源。本轮不将库存、SKU 明细、订单、支付或用户隐私写入 Elasticsearch。

## 5. 环境架构

### 5.1 服务组成

```text
Docker Compose
├── mysql                    现有，保持不变
├── redis                    现有，保持不变
├── langgraph-postgres       现有，保持不变
├── elasticsearch            新增，单节点实验环境
└── kibana                   新增，查询与观察界面
```

### 5.2 版本策略

- Elasticsearch 与 Kibana 使用完全相同的固定版本；
- 不使用 `latest` 镜像标签；
- 版本通过一个共享环境变量管理，Compose 中两个服务引用同一变量；
- 初始实现以设计实施时验证可用的 Elasticsearch `9.4.3` 与 Kibana `9.4.3` 为基线；
- SmartCN 插件版本必须与 Elasticsearch 服务端版本严格一致；
- 后续升级必须同时升级 Elasticsearch、Kibana 和插件，并重新执行本设计的验收查询。

### 5.3 单节点设置

本轮只运行一个 Elasticsearch 节点：

```text
discovery.type = single-node
主分片数 = 1
副本数 = 0
```

单节点无法分配副本。实验索引设置为 0 个副本，使 `product_v1` 自身可以达到 green。Kibana 可能创建带副本的系统索引，因此启动 Kibana 后整体集群允许为 yellow；验收时应单独检查 `product_v1`，不要把系统索引的未分配副本误判为商品索引故障。

### 5.4 资源约束

- Elasticsearch JVM 堆内存初始固定为 1 GB；
- Elasticsearch 与 Kibana 均设置容器资源边界，避免影响 MySQL、Redis、Java 后端和前端开发；
- Elasticsearch 数据目录使用命名卷持久化；
- 删除容器后重建不应丢失索引，只有显式删除数据卷才会清空实验数据。

### 5.5 本地安全边界

本轮是仅限本机的学习环境，可以采用简化的本地安全配置以降低 TLS 和证书管理对搜索学习的干扰，但必须满足：

- Elasticsearch 和 Kibana 端口只用于本机开发，不暴露到公网；
- 配置文件明确标注“不可用于生产”；
- 不在仓库中提交生产密码、API Key 或证书私钥；
- 后续进入部署设计时，必须单独设计认证、TLS、密钥托管和网络访问控制。

## 6. 索引模型

### 6.1 索引与别名

```text
物理索引：product_v1
业务别名：product_current
写索引：product_v1
```

别名应通过单个原子 alias 操作创建，并为 `product_v1` 设置 `is_write_index: true`。

### 6.2 商品文档

第一版实验文档结构：

```json
{
  "spuId": "1106",
  "spuCode": "PUFFER_WINTER_LIGHT_001",
  "name": "轻量保暖羽绒服",
  "description": "轻量羽绒服，适合冬季通勤和旅行。",
  "category": "羽绒服",
  "fitType": "合身",
  "materials": ["羽绒", "聚酯纤维"],
  "styles": ["通勤", "简约"],
  "scenes": ["通勤", "旅行"],
  "seasons": ["winter"],
  "status": "on_sale",
  "updatedAt": "2026-07-20T10:00:00+08:00"
}
```

`scenes` 当前不是 MySQL 中的独立事实表字段。本轮它是从商品描述、风格标签或扩展属性中人工整理的搜索派生字段。下一轮设计必须明确它的生成规则，不能把它当作已经存在的数据库事实字段。

### 6.3 字段职责

| 字段 | 主类型 | 搜索子字段 | 用途 |
|---|---|---|---|
| `spuId` | `keyword` | 无 | 文档业务标识、精确查询 |
| `spuCode` | `keyword` | 无 | 编码精确查询 |
| `name` | `text`，standard | `smartcn`、`keyword` | 默认/中文全文检索、精确值 |
| `description` | `text`，standard | `smartcn` | 默认/中文全文检索 |
| `category` | `keyword` | `search`，SmartCN | 精确过滤、全文检索 |
| `fitType` | `keyword` | `search`，SmartCN | 精确过滤、全文检索 |
| `materials` | `keyword` | `search`，SmartCN | 精确过滤、全文检索 |
| `styles` | `keyword` | `search`，SmartCN | 精确过滤、全文检索 |
| `scenes` | `keyword` | `search`，SmartCN | 精确过滤、全文检索 |
| `seasons` | `keyword` | 无 | 精确过滤 |
| `status` | `keyword` | 无 | 上下架状态过滤 |
| `updatedAt` | `date` | 无 | 更新时间、将来重建校验 |

数组字段无需声明特殊数组类型；同一字段写入多个同类型值即可。`keyword` 主字段用于 `term`、`terms`、聚合和精确值，`.search` 子字段用于被 analyzer 处理后的全文检索。

### 6.4 分词字段

`name` 和 `description` 同时保留两套全文字段：

```text
name                 standard
name.smartcn         SmartCN
description          standard
description.smartcn  SmartCN
```

标签类字段以精确过滤为主，只为全文搜索额外提供 SmartCN 子字段：

```text
category.search
fitType.search
materials.search
styles.search
scenes.search
```

## 7. 实验数据

从现有 Flyway 种子数据中选择 10 件覆盖不同类目、季节、风格和场景的商品：

1. 通勤轻薄外套；
2. 轻量保暖羽绒服；
3. 户外防风轻壳夹克；
4. 街头牛仔夹克；
5. 通勤轻薄风衣；
6. 极简针织开衫；
7. 羊毛保暖针织衫；
8. 通勤百褶半裙；
9. 男士牛津纺通勤衬衫；
10. 轻量运动夹克。

实验数据脚本必须标注其来源 SPU，并保持 `spuId`、`spuCode`、名称、描述和状态与迁移数据一致。材料、风格、季节等字段应优先取自相应关联表的种子数据；`scenes` 等派生值需单独标注。

## 8. 搜索实验设计

### 8.1 分词对比

使用 `_analyze` 分别测试 standard 与 SmartCN，至少覆盖：

```text
冬季通勤外套
春秋通勤夹克
羊毛保暖针织衫
通勤百褶半裙
户外防风
```

记录每种 analyzer 产生的 token、位置和命中差异。实验结论必须基于实际输出，不预设 SmartCN 对所有查询一定更优。

### 8.2 单字段查询

使用 `match` 对比：

```text
name
name.smartcn
description
description.smartcn
```

确认同一关键词在不同分析字段上的召回和排序差异。

### 8.3 多字段查询与权重

SmartCN 主实验查询使用：

```text
name.smartcn^5
styles.search^3
category.search^2
scenes.search^2
materials.search^1.5
description.smartcn
```

权重只是相关度计算的影响因子，不保证最终 `_score` 等于权重值。实验应至少做一次只改变字段权重的对照，并记录排名变化。

### 8.4 精确过滤

将全文相关度查询放在 `bool.must` 或 `bool.should` 中，将以下条件放在 `bool.filter` 中：

```text
status = on_sale
category = 指定类目
styles 包含指定标签
seasons 包含指定季节
```

过滤条件用于缩小结果集，不用于提高 `_score`。不得使用 `match` 替代 `keyword` 字段上的精确过滤。

### 8.5 高亮

至少对以下字段返回高亮片段：

```text
name.smartcn
description.smartcn
```

实验结果需要确认：

- 命中的关键词被标记；
- 没有高亮片段时客户端仍可使用原始字段；
- 高亮文本只用于展示，不作为可信 HTML 直接输出到业务页面。

### 8.6 相关度解释

对一个排名符合预期和一个排名不符合预期的文档分别执行 `_explain`。记录：

- 哪些字段命中；
- 字段 boost 如何参与计算；
- 词频、逆文档频率和字段长度如何影响结果；
- 为什么某个结果排在另一个结果之前。

## 9. 实验数据流

本轮数据流完全由开发者显式触发：

```text
Flyway 种子数据（事实参考）
  -> 手工整理实验 JSON
  -> Kibana Dev Tools / REST Bulk API
  -> product_current 别名
  -> product_v1 物理索引
  -> Kibana Dev Tools 执行查询
  -> 人工记录结果与结论
```

没有 Java 服务、定时任务或消息消费者参与这条链路。

## 10. 可重复执行的实验资产

实施时应在项目文档目录保存以下资产，避免操作只存在于 Kibana 历史中：

```text
docs/elasticsearch/
├── README.md                 启动、验证、停止、清理和故障排查
├── product-v1-index.json     settings、analysis 与 mappings
├── product-v1-seed.ndjson    10 件商品的 Bulk 数据
└── product-search-lab.http   analyze、search、filter、highlight、explain 示例
```

这些文件属于学习与运维资产，不是 Java 运行时代码。所有命令应能从空数据卷开始重复执行。

## 11. 故障与恢复

### 11.1 Elasticsearch 无法启动

优先检查：

- Docker 可用内存；
- JVM 堆设置；
- 数据卷权限；
- SmartCN 插件是否与 Elasticsearch 版本一致；
- 9200 端口是否占用。

Elasticsearch 失败不得影响现有 Java/MySQL 搜索，因为本轮不存在运行时依赖。

### 11.2 Kibana 无法连接

检查 Elasticsearch 健康状态、容器网络、服务地址和两者版本。Kibana 只用于观察和操作，Kibana 失败不代表 Elasticsearch 索引不可用。

### 11.3 Mapping 创建错误

本轮实验索引数据可重建，不在已有字段上直接修改不兼容 Mapping。处理方式是：

1. 修正 `product-v1-index.json`；
2. 删除错误实验索引；
3. 重新创建索引和别名；
4. 重新执行 Bulk 数据；
5. 重新执行验收查询。

若索引已经作为稳定基线使用，则创建新的版本索引，不复用旧版本名称。

### 11.4 数据卷损坏或被清理

索引不是事实源。使用版本化 Mapping 和 Bulk 种子文件即可重建，不从 Elasticsearch 反向恢复 MySQL。

## 12. 验证与验收

### 12.1 环境验收

- [ ] Elasticsearch 健康检查通过；
- [ ] Kibana 可以连接 Elasticsearch；
- [ ] Elasticsearch 与 Kibana 版本完全一致；
- [ ] SmartCN 插件可以正常使用；
- [ ] 重启容器后索引数据仍然存在；
- [ ] 原有 MySQL、Redis 和 LangGraph PostgreSQL 启动方式未被破坏。

### 12.2 索引验收

- [ ] `product_v1` 创建成功；
- [ ] `product_current` 指向 `product_v1`；
- [ ] `product_v1` 是别名的写索引；
- [ ] 索引为 1 个主分片、0 个副本；
- [ ] 10 件真实项目商品写入成功；
- [ ] `_count` 与预期商品数一致；
- [ ] 重复执行种子脚本不会产生重复文档。

### 12.3 搜索验收

- [ ] `_analyze` 能展示 standard 和 SmartCN 的 token 差异；
- [ ] `match` 能完成单字段搜索；
- [ ] `multi_match` 能完成多字段搜索；
- [ ] 改变字段 boost 后能观察到可解释的排名变化；
- [ ] `category`、`styles`、`seasons` 和 `status` 可精确过滤；
- [ ] 查询能够返回名称或描述高亮；
- [ ] `_explain` 能解释指定文档的命中和评分；
- [ ] 预设查询结果被记录，失败时能够区分分词、Mapping、数据和查询 DSL 问题。

### 12.4 边界验收

- [ ] Java `pom.xml` 没有新增 Elasticsearch 客户端；
- [ ] Java 商品搜索代码没有修改；
- [ ] 前端搜索代码没有修改；
- [ ] 没有新增 MySQL 自动同步；
- [ ] 没有新增 RabbitMQ、Outbox、CDC 或 RAG 功能。

## 13. 文档要求

实施文档需要说明：

- 如何启动和停止 Elasticsearch/Kibana；
- 如何判断两个服务健康；
- 如何创建、删除和重建实验索引；
- 如何写入实验商品；
- 如何运行每个搜索实验；
- 每个实验预期观察什么，而不仅是粘贴请求；
- 如何保留数据、如何完全清空数据；
- 本地安全配置为什么不能直接用于生产。

## 14. 后续方案 C 的入口条件

只有本设计的验收项全部通过后，才进入 Java 接入设计。下一轮至少需要重新讨论：

```text
MySQL 商品事实
  -> 全量重建 product_v2
  -> 原子切换 product_current
  -> Elasticsearch 返回 SPU ID、相关度和高亮
  -> MySQL 批量补充价格、库存、状态
  -> Redis 缓存搜索结果
  -> Elasticsearch 异常时回退 MySQL LIKE
```

下一轮还必须定义：

- `scenes` 等派生字段的确定性生成规则；
- 全量重建期间的别名切换方式；
- MySQL 与 Elasticsearch 的一致性边界；
- 搜索结果顺序在 MySQL 补数后如何保持；
- 超时、熔断、降级和观测指标；
- Java API Client 与服务端版本兼容策略；
- 商品修改后的增量同步是否需要在后续阶段引入 Outbox + RabbitMQ。

## 15. 参考资料

- Elastic 官方自托管安装说明：<https://www.elastic.co/docs/deploy-manage/deploy/self-managed/installing-elasticsearch>
- Elastic 官方分析插件说明：<https://www.elastic.co/docs/reference/elasticsearch/plugins/analysis-plugins>
- Elastic 官方字段类型说明：<https://www.elastic.co/docs/reference/elasticsearch/mapping-reference/field-data-types>
- Elastic 官方别名说明：<https://www.elastic.co/docs/manage-data/data-store/aliases>
