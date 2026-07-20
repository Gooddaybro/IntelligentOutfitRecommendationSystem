# Elasticsearch 商品搜索实验

本目录同时保存搜索实验和 Java 接入说明。Elasticsearch 只是可删除、可重建的搜索副本，MySQL 仍然是商品、价格、库存和上下架状态的事实源。

> **安全边界：**本地 Compose 为了降低学习成本关闭了 Elasticsearch 安全认证，只允许用于本机学习，不能直接用于生产或暴露到公网。

## 文件说明

| 文件 | 作用 |
|---|---|
| `../../backend/src/main/resources/elasticsearch/product-index.json` | Java 重建与手工实验共用的分片和 Mapping 定义。 |
| `product-v1-seed.ndjson` | 使用稳定 SPU ID 写入 10 件真实项目商品，可重复执行。 |
| `product-search-lab.http` | 保存分词、查询、过滤、高亮和评分解释实验。 |

## 启动

在项目根目录执行：

```powershell
docker compose up -d elasticsearch kibana
docker compose ps elasticsearch kibana
```

第一次启动需要下载镜像和 SmartCN 插件，耗时取决于网络。访问地址：

- Elasticsearch：`http://localhost:9200`
- Kibana：`http://localhost:5601`

## 健康检查

```powershell
$health = Invoke-RestMethod http://localhost:9200/_cluster/health
$health | Select-Object cluster_name, status, number_of_nodes

$productHealth = Invoke-RestMethod http://localhost:9200/_cluster/health/product_v1
$productHealth | Select-Object status, active_primary_shards, unassigned_shards

$plugins = Invoke-RestMethod 'http://localhost:9200/_cat/plugins?format=json'
$plugins | Select-Object name, component, version

$kibana = Invoke-WebRequest -UseBasicParsing -TimeoutSec 10 http://localhost:5601/api/status
$kibana.StatusCode
```

预期结果：

- `product_v1` 商品实验索引状态为 `green`；
- Kibana 创建的部分系统索引在单节点上可能保留未分配副本，因此整体集群允许为 `yellow`；
- 节点数为 1；
- 插件列表包含 `analysis-smartcn`；
- Kibana 状态接口返回 HTTP 200；
- Elasticsearch 与 Kibana 使用同一个 `ELASTIC_STACK_VERSION`。

## 创建索引

创建实验索引后，再单独创建 `product_current` 写别名：

```powershell
$body = Get-Content -Raw -Encoding UTF8 backend/src/main/resources/elasticsearch/product-index.json
Invoke-RestMethod `
    -Method Put `
    -Uri http://localhost:9200/product_v1 `
    -ContentType application/json `
    -Body $body

$aliasBody = '{"actions":[{"add":{"index":"product_v1","alias":"product_current","is_write_index":true}}]}'
Invoke-RestMethod -Method Post -Uri http://localhost:9200/_aliases `
    -ContentType application/json -Body $aliasBody
```

如果返回“索引已经存在”，请不要直接覆盖 Mapping；使用后面的重建步骤恢复实验环境。

## 写入实验数据

```powershell
$seed = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson
$bulk = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:9200/_bulk?refresh=true' `
    -ContentType 'application/x-ndjson; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($seed))

if ($bulk.errors) {
    $bulk.items | ConvertTo-Json -Depth 10
    throw 'Bulk 写入包含失败项'
}

Invoke-RestMethod http://localhost:9200/product_current/_count
```

文档数应为 10。每件商品都使用 SPU ID 作为 `_id`，重复执行 Bulk 请求后文档数仍应为 10。

这里显式转换为 UTF-8 字节，是为了避免 Windows PowerShell 把中文请求体按系统默认编码发送成问号。

## 搜索实验

可以使用两种方式执行 [product-search-lab.http](product-search-lab.http)：

1. 使用支持 `.http` 文件的编辑器逐项运行；
2. 打开 Kibana Dev Tools，把请求中的路径、请求体复制进去执行，并去掉 `http://localhost:9200` 主机部分。

建议按文件顺序观察：

1. `standard` 与 `smartcn` 产生的 token；
2. `name` 与 `name.smartcn` 的召回差异；
3. `name.smartcn^5` 等 boost 对排名的影响；
4. `filter` 如何缩小结果集但不增加 `_score`；
5. 名称和描述的高亮片段；
6. `_explain` 中字段命中、词频和权重对评分的贡献。

不要预设 SmartCN 对所有查询一定更好，结论应以实际 token、召回和排名为准。

## 停止与恢复

停止实验服务但保留数据：

```powershell
docker compose stop kibana elasticsearch
```

重新启动：

```powershell
docker compose up -d elasticsearch kibana
Invoke-RestMethod http://localhost:9200/product_current/_count
```

命名卷仍在时，重新启动后的文档数应保持为 10。

## 重建索引

### 由 Java 从 MySQL 全量重建（推荐）

启动后端时显式开启 ES：

```powershell
$env:APP_ELASTICSEARCH_ENABLED = 'true'
cd backend
.\mvnw.cmd spring-boot:run
```

然后调用内部运维接口：

```powershell
Invoke-RestMethod `
    -Method Post `
    -Uri http://localhost:8080/internal/search/products/rebuild `
    -Headers @{ 'X-Internal-Token' = 'dev-internal-token' }
```

该操作会读取 MySQL 商品事实，创建带 UTC 时间戳的新物理索引，批量写入并校验数量，最后在单个原子操作中把 `product_current` 切换到新索引。并发重复提交会被拒绝，失败时旧索引仍可搜索。

重建同时执行索引生命周期治理：

- 重建失败时，只删除本次已经确认创建的半成品索引；
- 重建成功后，默认保留当前索引和最近两个历史索引；
- `product_v1` 等不符合“配置前缀 + 14 位 UTC 时间戳”规则的实验索引不会被自动删除；
- 历史清理失败不会回滚已经成功的别名切换，下次重建会再次尝试。

历史索引数量可以通过环境变量调整，数值不包含当前索引：

```powershell
$env:APP_ELASTICSEARCH_RETAINED_HISTORY_COUNT = '2'
```

设置为 `0` 表示成功后只保留当前索引。负数会使应用启动失败，防止产生含义不明确的删除策略。

商城的 `/api/products` 与内部 `/internal/products/search` 地址保持不变。开启 ES 时先使用 ES 找到有序 SPU ID，再从 MySQL 补齐当前价格和上下架状态；连接失败、服务端错误或别名尚不存在时自动回退到 MySQL LIKE。功能开关默认关闭，因此没有 ES 的环境不受影响。

### 手工恢复实验索引

`product_v1` 是实验索引，Mapping 错误时从版本化文件重建，不在原索引上强行修改不兼容字段：

```powershell
Invoke-RestMethod -Method Delete http://localhost:9200/product_v1

$body = Get-Content -Raw -Encoding UTF8 backend/src/main/resources/elasticsearch/product-index.json
Invoke-RestMethod `
    -Method Put `
    -Uri http://localhost:9200/product_v1 `
    -ContentType application/json `
    -Body $body

$seed = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson
$bulk = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:9200/_bulk?refresh=true' `
    -ContentType 'application/x-ndjson; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($seed))

if ($bulk.errors) { throw '重建后的 Bulk 写入包含失败项' }

$aliasBody = '{"actions":[{"remove":{"index":"product_*","alias":"product_current","must_exist":false}},{"add":{"index":"product_v1","alias":"product_current","is_write_index":true}}]}'
Invoke-RestMethod -Method Post -Uri http://localhost:9200/_aliases `
    -ContentType application/json -Body $aliasBody
```

后续真实升级应创建 `product_v2` 并原子切换 `product_current`，而不是复用已经稳定运行的物理索引名称。

### 人工回滚

自动保留的两个历史索引只提供人工回滚窗口，不会自动回滚。确认目标历史索引文档完整后，使用单个 `_aliases` 请求移除当前指向并添加历史指向；不要先删别名再单独添加，否则会产生短暂无别名窗口。回滚后，当前别名目标仍受清理策略保护。

## 完全清理

删除数据卷会永久清空实验索引。以下命令只删除本实验的两个容器和名称完全匹配的数据卷：

```powershell
docker compose stop kibana elasticsearch
docker compose rm -f kibana elasticsearch
docker volume rm intelligent_outfit_elasticsearch_data
```

不要使用模糊匹配或批量清理命令删除其他项目卷。

## 常见问题

### 开启 ES 后仍然返回旧搜索结果

商城搜索沿用现有 Redis 缓存，缓存键不变，默认存在数分钟可接受的一致性窗口。开发验证时可以等待缓存过期，或仅清理对应的 `product:search:*` 键；不要清空整个 Redis。

### 9200 或 5601 端口被占用

在当前 PowerShell 会话设置新端口后启动：

```powershell
$env:ELASTICSEARCH_HOST_PORT = '19200'
$env:KIBANA_HOST_PORT = '15601'
docker compose up -d elasticsearch kibana
```

后续命令也需要把 `localhost:9200` 替换为实际端口。

### Elasticsearch 反复退出

```powershell
docker compose logs --tail 200 elasticsearch
```

重点检查 Docker 可用内存、JVM 堆设置、数据卷权限和插件下载错误。

### SmartCN 不存在

```powershell
Invoke-RestMethod 'http://localhost:9200/_cat/plugins?format=json'
docker compose logs --tail 200 elasticsearch
```

确认挂载了 `docker/elasticsearch/elasticsearch-plugins.yml`，并确认 SmartCN 与 Elasticsearch 使用相同版本。

### 整体集群状态为 yellow

```powershell
Invoke-RestMethod http://localhost:9200/product_v1/_settings
```

先检查 `product_v1` 是否为 `green`。单节点实验索引的 `number_of_replicas` 必须为 0；否则副本无法分配。Kibana 自己创建的系统索引可能带有副本，使整体集群保持 `yellow`，这不代表 `product_v1` 不可用。

### Bulk API 报错

确认 NDJSON 每个 action 后紧跟一个文档，而且文件最后保留换行。查看 `$bulk.items` 中具体失败项，不要只检查 HTTP 状态码。

## ?????????P2?

?????? AI ??????????????????

```text
product_search_outbox
    ? Product Search Relay
    ? product.search.reindex.v1
    ? Product Search Worker
    ? product_search_inbox
    ? Elasticsearch product_current
```

### ?????

?????????Web ?????? Outbox ??????Worker ????????? ES?

```powershell
# Web????????????
$env:SPRING_PROFILES_ACTIVE = 'web'
$env:APP_ELASTICSEARCH_ENABLED = 'true'
$env:APP_PRODUCT_SEARCH_SYNC_ENABLED = 'true'
$env:APP_PRODUCT_SEARCH_SYNC_PUBLISHER_ENABLED = 'true'

# Worker???????????????
$env:SPRING_PROFILES_ACTIVE = 'worker'
$env:APP_ELASTICSEARCH_ENABLED = 'true'
$env:APP_PRODUCT_SEARCH_SYNC_ENABLED = 'true'
$env:APP_PRODUCT_SEARCH_SYNC_LISTENER_ENABLED = 'true'
```

??????????

```powershell
docker compose up -d mysql redis rabbitmq elasticsearch kibana
```

RabbitMQ ????? `http://localhost:15672`???????? `app`???? `app-dev-password`?

### ????

- ?????????????????????? MySQL ???? `product_search_outbox`??????????????
- ????? `eventId`?`spuId`?`eventType`?`occurredAt` ? `schemaVersion`?Worker ???? MySQL ?????
- ?? `_id` ??? SPU ID???????????????????????????? ES ???
- Worker ???? ES ??? `product_search_inbox`????????????????
- ???????? 10 ??60 ??300 ????????????? `product.search.reindex.dlq.v1`?????? ES ??? 4xx ?????? DLQ?
- ???????????? W0???????? W1???? `(W0, W1]` ????? SPU??????????????

### ????

```sql
SELECT id, event_id, spu_id, status, publish_attempts, last_error, created_at
FROM product_search_outbox
ORDER BY id DESC
LIMIT 50;

SELECT consumer_name, event_id, spu_id, processed_at
FROM product_search_inbox
ORDER BY processed_at DESC
LIMIT 50;
```

?? DLQ ???? Mapping???? ES ??????????????????? `product.search.reindex.v1`?????? DLQ ?????????? Inbox ?????????????????????? ID?

### ????

?? Redis ?????????????????????????? TTL ?????????? P3?????????????????????? Redis ????
