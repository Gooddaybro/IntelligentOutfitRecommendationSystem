# Elasticsearch Product Search Lab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a reproducible local Elasticsearch and Kibana lab that indexes ten real catalog products and demonstrates Chinese analysis, weighted search, filtering, highlighting, and score explanation without changing the Java search path.

**Architecture:** Extend the existing Docker Compose stack with isolated Elasticsearch and Kibana services. Store the versioned index definition, idempotent Bulk seed data, executable HTTP experiments, and operating guide under `docs/elasticsearch`; the existing Java, MySQL, Redis, and frontend paths remain independent.

**Tech Stack:** Docker Compose, Elasticsearch 9.4.3, Kibana 9.4.3, SmartCN analysis plugin, Elasticsearch REST APIs, Kibana Dev Tools, PowerShell verification.

---

## File Structure

| File | Responsibility |
|---|---|
| `docker-compose.yml` | Add the local Elasticsearch and Kibana services and the persistent Elasticsearch volume. |
| `docker/elasticsearch/elasticsearch-plugins.yml` | Declaratively install the version-matched SmartCN plugin in the official Elasticsearch image. |
| `docs/elasticsearch/product-v1-index.json` | Define `product_v1` settings, analyzers, mappings, and the `product_current` alias. |
| `docs/elasticsearch/product-v1-seed.ndjson` | Idempotently seed ten real project SPUs with stable document IDs. |
| `docs/elasticsearch/product-search-lab.http` | Preserve executable analyze, match, multi-match, filter, highlight, and explain requests. |
| `docs/elasticsearch/README.md` | Explain startup, verification, experiments, reset, and local-only security boundaries. |

No Java, frontend, Flyway, or application configuration file is modified in this plan.

### Task 1: Add Elasticsearch and Kibana to Docker Compose

**Files:**
- Modify: `docker-compose.yml`
- Create: `docker/elasticsearch/elasticsearch-plugins.yml`

- [ ] **Step 1: Verify the services do not exist yet**

Run:

```powershell
$services = docker compose config --services
if ($services -contains 'elasticsearch' -or $services -contains 'kibana') {
    throw 'Elasticsearch lab services already exist'
}
```

Expected: command exits successfully because neither service is present.

- [ ] **Step 2: Add the declarative SmartCN plugin file**

Create `docker/elasticsearch/elasticsearch-plugins.yml`:

```yaml
plugins:
  - id: analysis-smartcn
```

- [ ] **Step 3: Extend `docker-compose.yml`**

Add these services after `langgraph-postgres`:

```yaml
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:${ELASTIC_STACK_VERSION:-9.4.3}
    container_name: intelligent_outfit_elasticsearch
    environment:
      discovery.type: single-node
      xpack.security.enabled: "false"
      ES_JAVA_OPTS: -Xms1g -Xmx1g
    ports:
      - "${ELASTICSEARCH_HOST_PORT:-9200}:9200"
    volumes:
      - intelligent_outfit_elasticsearch_data:/usr/share/elasticsearch/data
      - ./docker/elasticsearch/elasticsearch-plugins.yml:/usr/share/elasticsearch/config/elasticsearch-plugins.yml:ro
    healthcheck:
      test: ["CMD-SHELL", "curl --fail --silent http://localhost:9200/_cluster/health >/dev/null || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 30
      start_period: 40s
    mem_limit: 2g

  kibana:
    image: docker.elastic.co/kibana/kibana:${ELASTIC_STACK_VERSION:-9.4.3}
    container_name: intelligent_outfit_kibana
    environment:
      ELASTICSEARCH_HOSTS: http://elasticsearch:9200
    ports:
      - "${KIBANA_HOST_PORT:-5601}:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    mem_limit: 1g
```

Add this volume under the existing `volumes:` block:

```yaml
  intelligent_outfit_elasticsearch_data:
    name: intelligent_outfit_elasticsearch_data
```

- [ ] **Step 4: Validate the merged Compose model**

Run:

```powershell
docker compose config --quiet
$services = docker compose config --services
if ($services -notcontains 'elasticsearch' -or $services -notcontains 'kibana') {
    throw 'Elasticsearch or Kibana is missing from the Compose model'
}
```

Expected: exit code 0 and both new services are present.

- [ ] **Step 5: Confirm existing services remain present**

Run:

```powershell
$services = docker compose config --services
@('mysql', 'redis', 'langgraph-postgres') | ForEach-Object {
    if ($services -notcontains $_) { throw "Existing service missing: $_" }
}
```

Expected: exit code 0.

- [ ] **Step 6: Commit the environment change**

```powershell
git add docker-compose.yml docker/elasticsearch/elasticsearch-plugins.yml
git commit -m "infra: add Elasticsearch search lab"
```

### Task 2: Define the Versioned Product Index

**Files:**
- Create: `docs/elasticsearch/product-v1-index.json`

- [ ] **Step 1: Add a failing structural check**

Run before creating the file:

```powershell
if (-not (Test-Path docs/elasticsearch/product-v1-index.json)) {
    throw 'product_v1 index definition is missing'
}
```

Expected: FAIL with `product_v1 index definition is missing`.

- [ ] **Step 2: Create the complete index definition**

Create `docs/elasticsearch/product-v1-index.json`:

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "spuId": { "type": "keyword" },
      "spuCode": { "type": "keyword" },
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "smartcn": { "type": "text", "analyzer": "smartcn" },
          "keyword": { "type": "keyword" }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "smartcn": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "category": {
        "type": "keyword",
        "fields": {
          "search": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "fitType": {
        "type": "keyword",
        "fields": {
          "search": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "materials": {
        "type": "keyword",
        "fields": {
          "search": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "styles": {
        "type": "keyword",
        "fields": {
          "search": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "scenes": {
        "type": "keyword",
        "fields": {
          "search": { "type": "text", "analyzer": "smartcn" }
        }
      },
      "seasons": { "type": "keyword" },
      "status": { "type": "keyword" },
      "updatedAt": { "type": "date" }
    }
  },
  "aliases": {
    "product_current": {
      "is_write_index": true
    }
  }
}
```

- [ ] **Step 3: Validate JSON and required mapping decisions**

Run:

```powershell
$index = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-index.json | ConvertFrom-Json
if ($index.settings.number_of_shards -ne 1) { throw 'Expected one primary shard' }
if ($index.settings.number_of_replicas -ne 0) { throw 'Expected zero replicas' }
if ($index.mappings.dynamic -ne 'strict') { throw 'Expected strict mapping' }
if ($index.mappings.properties.name.fields.smartcn.analyzer -ne 'smartcn') { throw 'SmartCN name field missing' }
if (-not $index.aliases.product_current.is_write_index) { throw 'Write alias missing' }
```

Expected: exit code 0.

- [ ] **Step 4: Commit the index definition**

```powershell
git add docs/elasticsearch/product-v1-index.json
git commit -m "docs: define product search index"
```

### Task 3: Add Idempotent Real-Product Seed Data

**Files:**
- Create: `docs/elasticsearch/product-v1-seed.ndjson`

- [ ] **Step 1: Verify the seed file is absent**

Run:

```powershell
if (-not (Test-Path docs/elasticsearch/product-v1-seed.ndjson)) {
    throw 'product search seed data is missing'
}
```

Expected: FAIL with `product search seed data is missing`.

- [ ] **Step 2: Create stable Bulk seed data**

Create `docs/elasticsearch/product-v1-seed.ndjson`. Each product uses its SPU ID as the Elasticsearch `_id`, so rerunning the Bulk request overwrites rather than duplicates documents:

```ndjson
{"index":{"_index":"product_current","_id":"1002"}}
{"spuId":"1002","spuCode":"JACKET_COMMUTE_001","name":"通勤轻薄外套","description":"轻薄通勤外套，适合春秋通勤和日常外出。","category":"外套","fitType":"合身","materials":["聚酯纤维"],"styles":["通勤","简洁","百搭"],"scenes":["通勤","日常"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1101"}}
{"spuId":"1101","spuCode":"OXFORD_SHIRT_COMMUTE_001","name":"男士牛津纺通勤衬衫","description":"挺括牛津纺衬衫，适合通勤、会议和精致休闲场景。","category":"衬衫","fitType":"合身","materials":["棉"],"styles":["通勤","精致休闲"],"scenes":["通勤","会议"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1104"}}
{"spuId":"1104","spuCode":"KNIT_MINIMAL_CARDIGAN_001","name":"极简针织开衫","description":"细针针织开衫，适合春秋通勤和室内空调环境。","category":"针织衫","fitType":"合身","materials":["针织"],"styles":["极简","通勤"],"scenes":["通勤","室内"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1106"}}
{"spuId":"1106","spuCode":"PUFFER_WINTER_LIGHT_001","name":"轻量保暖羽绒服","description":"轻量羽绒服，适合冬季通勤和旅行。","category":"羽绒服","fitType":"合身","materials":["羽绒","聚酯纤维"],"styles":["通勤","简约"],"scenes":["通勤","旅行"],"seasons":["winter"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1112"}}
{"spuId":"1112","spuCode":"JACKET_OUTDOOR_SHELL_001","name":"户外防风轻壳夹克","description":"尼龙防风轻壳，适合春秋户外和城市通勤。","category":"外套","fitType":"合身","materials":["尼龙"],"styles":["户外","运动"],"scenes":["户外","城市通勤"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1116"}}
{"spuId":"1116","spuCode":"KNIT_WOOL_WINTER_001","name":"羊毛保暖针织衫","description":"羊毛针织衫，适合冬季内搭和轻商务。","category":"针织衫","fitType":"合身","materials":["羊毛"],"styles":["保暖","轻商务"],"scenes":["通勤","商务"],"seasons":["winter"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1118"}}
{"spuId":"1118","spuCode":"DENIM_JACKET_STREET_001","name":"街头牛仔夹克","description":"牛仔面料夹克，适合春秋街头穿搭。","category":"外套","fitType":"廓形","materials":["牛仔布"],"styles":["街头","休闲"],"scenes":["日常","街头"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1120"}}
{"spuId":"1120","spuCode":"SKIRT_COMMUTE_PLEATED_001","name":"通勤百褶半裙","description":"垂顺百褶半裙，适合春秋通勤。","category":"半裙","fitType":"合身","materials":["聚酯纤维"],"styles":["通勤","优雅"],"scenes":["通勤"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1124"}}
{"spuId":"1124","spuCode":"JACKET_COMMUTE_TRENCH_001","name":"通勤轻薄风衣","description":"轻薄风衣，适合春秋通勤和出差。","category":"外套","fitType":"合身","materials":["聚酯纤维"],"styles":["通勤","简约"],"scenes":["通勤","出差"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
{"index":{"_index":"product_current","_id":"1132"}}
{"spuId":"1132","spuCode":"JACKET_SPORT_LIGHT_001","name":"轻量运动夹克","description":"轻量运动夹克，适合通勤前后轻运动。","category":"外套","fitType":"合身","materials":["聚酯纤维"],"styles":["运动","轻量"],"scenes":["通勤","运动"],"seasons":["spring","autumn"],"status":"on_sale","updatedAt":"2026-07-20T10:00:00+08:00"}
```

The file must end with a newline because the Bulk API requires it.

- [ ] **Step 3: Validate the NDJSON pairs and document count**

Run:

```powershell
$lines = @(Get-Content -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson | Where-Object { $_.Trim() })
if ($lines.Count -ne 20) { throw "Expected 20 NDJSON lines, got $($lines.Count)" }
for ($i = 0; $i -lt $lines.Count; $i++) {
    $null = $lines[$i] | ConvertFrom-Json
}
$ids = for ($i = 0; $i -lt $lines.Count; $i += 2) {
    (($lines[$i] | ConvertFrom-Json).index)._id
}
if (($ids | Sort-Object -Unique).Count -ne 10) { throw 'Expected ten unique document IDs' }
```

Expected: exit code 0.

- [ ] **Step 4: Commit the seed data**

```powershell
git add docs/elasticsearch/product-v1-seed.ndjson
git commit -m "docs: add product search lab data"
```

### Task 4: Preserve Executable Search Experiments

**Files:**
- Create: `docs/elasticsearch/product-search-lab.http`

- [ ] **Step 1: Verify the experiment file is absent**

Run:

```powershell
if (-not (Test-Path docs/elasticsearch/product-search-lab.http)) {
    throw 'search experiment requests are missing'
}
```

Expected: FAIL with `search experiment requests are missing`.

- [ ] **Step 2: Create the executable request collection**

Create `docs/elasticsearch/product-search-lab.http` with these request sections:

```http
### Cluster health
GET http://localhost:9200/_cluster/health

### Installed plugins
GET http://localhost:9200/_cat/plugins?v

### Standard analyzer
POST http://localhost:9200/_analyze
Content-Type: application/json

{"analyzer":"standard","text":"冬季通勤外套"}

### SmartCN analyzer
POST http://localhost:9200/_analyze
Content-Type: application/json

{"analyzer":"smartcn","text":"冬季通勤外套"}

### Standard name match
POST http://localhost:9200/product_current/_search
Content-Type: application/json

{"query":{"match":{"name":"冬季通勤外套"}}}

### SmartCN name match
POST http://localhost:9200/product_current/_search
Content-Type: application/json

{"query":{"match":{"name.smartcn":"冬季通勤外套"}}}

### Weighted multi-field search with exact filters and highlights
POST http://localhost:9200/product_current/_search
Content-Type: application/json

{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "冬季通勤外套",
            "fields": [
              "name.smartcn^5",
              "styles.search^3",
              "category.search^2",
              "scenes.search^2",
              "materials.search^1.5",
              "description.smartcn"
            ]
          }
        }
      ],
      "filter": [
        { "term": { "status": "on_sale" } }
      ]
    }
  },
  "highlight": {
    "pre_tags": ["<em>"],
    "post_tags": ["</em>"],
    "fields": {
      "name.smartcn": {},
      "description.smartcn": {}
    }
  }
}

### Exact category, season, and status filters
POST http://localhost:9200/product_current/_search
Content-Type: application/json

{
  "query": {
    "bool": {
      "must": [
        { "match": { "description.smartcn": "通勤" } }
      ],
      "filter": [
        { "term": { "category": "外套" } },
        { "term": { "seasons": "autumn" } },
        { "term": { "status": "on_sale" } }
      ]
    }
  }
}

### Explain one known document
POST http://localhost:9200/product_current/_explain/1106
Content-Type: application/json

{
  "query": {
    "multi_match": {
      "query": "冬季通勤外套",
      "fields": ["name.smartcn^5", "styles.search^3", "category.search^2", "description.smartcn"]
    }
  }
}
```

- [ ] **Step 3: Check every required experiment exists**

Run:

```powershell
$lab = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-search-lab.http
@('_analyze', 'standard', 'smartcn', 'match', 'multi_match', 'filter', 'highlight', '_explain') | ForEach-Object {
    if (-not $lab.Contains($_)) { throw "Missing experiment token: $_" }
}
```

Expected: exit code 0.

- [ ] **Step 4: Commit the experiment collection**

```powershell
git add docs/elasticsearch/product-search-lab.http
git commit -m "docs: add Elasticsearch search experiments"
```

### Task 5: Write the Operating and Learning Guide

**Files:**
- Create: `docs/elasticsearch/README.md`

- [ ] **Step 1: Verify the guide is absent**

Run:

```powershell
if (-not (Test-Path docs/elasticsearch/README.md)) {
    throw 'Elasticsearch lab guide is missing'
}
```

Expected: FAIL with `Elasticsearch lab guide is missing`.

- [ ] **Step 2: Write the guide with exact commands**

Create `docs/elasticsearch/README.md` with these sections and commands:

````markdown
# Elasticsearch 商品搜索实验

本实验只验证搜索能力，不修改 Java、前端或 MySQL 商品搜索链路。本地 Compose 关闭了 Elasticsearch 安全认证，只允许用于本机学习，不能直接用于生产。

## 启动

```powershell
docker compose up -d elasticsearch kibana
docker compose ps elasticsearch kibana
```

访问地址：

- Elasticsearch：`http://localhost:9200`
- Kibana：`http://localhost:5601`

## 健康检查

```powershell
Invoke-RestMethod http://localhost:9200/_cluster/health
Invoke-RestMethod http://localhost:9200/_cat/plugins?format=json
```

集群应为 `green`，插件列表应包含 `analysis-smartcn`。

## 创建索引

```powershell
$body = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-index.json
Invoke-RestMethod -Method Put -Uri http://localhost:9200/product_v1 -ContentType application/json -Body $body
Invoke-RestMethod http://localhost:9200/_alias/product_current
```

## 写入实验数据

```powershell
$seed = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson
Invoke-RestMethod -Method Post -Uri 'http://localhost:9200/_bulk?refresh=true' -ContentType application/x-ndjson -Body $seed
Invoke-RestMethod http://localhost:9200/product_current/_count
```

文档数应为 10。重复执行 Bulk 请求后仍应为 10。

## 搜索实验

打开 Kibana Dev Tools，将 `product-search-lab.http` 中的请求路径和请求体复制执行；也可以使用支持 `.http` 文件的客户端逐项执行。

依次观察：standard 与 SmartCN token、单字段召回、多字段权重、filter 不计分、高亮片段和 `_explain` 评分细节。

## 停止与恢复

```powershell
docker compose stop kibana elasticsearch
docker compose up -d elasticsearch kibana
```

重新启动后 `_count` 仍应为 10。

## 重建索引

```powershell
Invoke-RestMethod -Method Delete http://localhost:9200/product_v1
$body = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-index.json
Invoke-RestMethod -Method Put -Uri http://localhost:9200/product_v1 -ContentType application/json -Body $body
$seed = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson
Invoke-RestMethod -Method Post -Uri 'http://localhost:9200/_bulk?refresh=true' -ContentType application/x-ndjson -Body $seed
```

## 完全清理

删除数据卷会永久清空实验索引。以下命令只删除本实验的两个容器和名称完全匹配的数据卷：

```powershell
docker compose stop kibana elasticsearch
docker compose rm -f kibana elasticsearch
docker volume rm intelligent_outfit_elasticsearch_data
```

不要使用模糊匹配删除其他项目卷。

## 常见问题

- 9200 被占用：设置 `ELASTICSEARCH_HOST_PORT` 后重新启动。
- 5601 被占用：设置 `KIBANA_HOST_PORT` 后重新启动。
- Elasticsearch 反复退出：检查 Docker 可用内存和容器日志。
- SmartCN 不存在：检查插件配置挂载、插件下载日志以及插件与 Elasticsearch 版本是否一致。
- 集群为 yellow：确认实验索引的 `number_of_replicas` 为 0。
- Mapping 错误：删除可重建的 `product_v1`，修正版本化定义后重新创建，不直接修改不兼容字段类型。
````

- [ ] **Step 3: Verify required operational topics are documented**

Run:

```powershell
$readme = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/README.md
@('启动', '健康检查', '创建索引', '写入实验数据', '搜索实验', '停止与恢复', '重建索引', '完全清理', '常见问题', '不能直接用于生产') | ForEach-Object {
    if (-not $readme.Contains($_)) { throw "Missing README topic: $_" }
}
```

Expected: exit code 0.

- [ ] **Step 4: Commit the operating guide**

```powershell
git add docs/elasticsearch/README.md
git commit -m "docs: add Elasticsearch lab guide"
```

### Task 6: Run the End-to-End Lab Acceptance

**Files:**
- Verify: `docker-compose.yml`
- Verify: `docker/elasticsearch/elasticsearch-plugins.yml`
- Verify: `docs/elasticsearch/product-v1-index.json`
- Verify: `docs/elasticsearch/product-v1-seed.ndjson`
- Verify: `docs/elasticsearch/product-search-lab.http`
- Verify: `docs/elasticsearch/README.md`

- [ ] **Step 1: Run all static checks**

Run:

```powershell
docker compose config --quiet
Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-index.json | ConvertFrom-Json | Out-Null
$lines = @(Get-Content -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson | Where-Object { $_.Trim() })
if ($lines.Count -ne 20) { throw 'Seed file must contain ten Bulk action/document pairs' }
$lines | ForEach-Object { $_ | ConvertFrom-Json | Out-Null }
git diff --check
```

Expected: exit code 0.

- [ ] **Step 2: Start Elasticsearch and Kibana**

Run:

```powershell
docker compose up -d elasticsearch kibana
docker compose ps elasticsearch kibana
```

Expected: Elasticsearch becomes healthy and Kibana remains running.

- [ ] **Step 3: Verify cluster and plugin health**

Run:

```powershell
$health = Invoke-RestMethod http://localhost:9200/_cluster/health
if ($health.status -ne 'green') { throw "Expected green cluster, got $($health.status)" }
$plugins = Invoke-RestMethod 'http://localhost:9200/_cat/plugins?format=json'
if ('analysis-smartcn' -notin $plugins.component) { throw 'SmartCN plugin is not installed' }
```

Expected: exit code 0.

- [ ] **Step 4: Create the index and seed it twice**

Run:

```powershell
$indexBody = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-index.json
Invoke-RestMethod -Method Put -Uri http://localhost:9200/product_v1 -ContentType application/json -Body $indexBody | Out-Null
$seed = Get-Content -Raw -Encoding UTF8 docs/elasticsearch/product-v1-seed.ndjson
1..2 | ForEach-Object {
    $bulk = Invoke-RestMethod -Method Post -Uri 'http://localhost:9200/_bulk?refresh=true' -ContentType application/x-ndjson -Body $seed
    if ($bulk.errors) { throw 'Bulk indexing returned errors' }
}
$count = Invoke-RestMethod http://localhost:9200/product_current/_count
if ($count.count -ne 10) { throw "Expected 10 products, got $($count.count)" }
```

Expected: both Bulk calls succeed and count remains 10.

- [ ] **Step 5: Verify the analyzer comparison**

Run:

```powershell
$standard = Invoke-RestMethod -Method Post -Uri http://localhost:9200/_analyze -ContentType application/json -Body '{"analyzer":"standard","text":"冬季通勤外套"}'
$smartcn = Invoke-RestMethod -Method Post -Uri http://localhost:9200/_analyze -ContentType application/json -Body '{"analyzer":"smartcn","text":"冬季通勤外套"}'
$standardTokens = @($standard.tokens.token)
$smartcnTokens = @($smartcn.tokens.token)
if ($standardTokens.Count -eq 0 -or $smartcnTokens.Count -eq 0) { throw 'Analyzer returned no tokens' }
Write-Output "standard: $($standardTokens -join ' / ')"
Write-Output "smartcn: $($smartcnTokens -join ' / ')"
```

Expected: both token lists are non-empty and printed for comparison.

- [ ] **Step 6: Verify weighted search, filtering, and highlighting**

Run the weighted request from `product-search-lab.http` and assert:

```powershell
$query = @'
{"query":{"bool":{"must":[{"multi_match":{"query":"冬季通勤外套","fields":["name.smartcn^5","styles.search^3","category.search^2","scenes.search^2","materials.search^1.5","description.smartcn"]}}],"filter":[{"term":{"status":"on_sale"}}]}},"highlight":{"fields":{"name.smartcn":{},"description.smartcn":{}}}}
'@
$result = Invoke-RestMethod -Method Post -Uri http://localhost:9200/product_current/_search -ContentType application/json -Body $query
if ($result.hits.total.value -lt 1) { throw 'Expected at least one search hit' }
if (-not $result.hits.hits[0]._score) { throw 'Expected a relevance score' }
if (-not $result.hits.hits[0].highlight) { throw 'Expected a highlight fragment on the top hit' }
```

Expected: at least one scored hit and a highlight on the top result.

- [ ] **Step 7: Verify persistence across restart**

Run:

```powershell
docker compose restart elasticsearch kibana
Start-Sleep -Seconds 30
$count = Invoke-RestMethod http://localhost:9200/product_current/_count
if ($count.count -ne 10) { throw 'Product documents did not survive restart' }
```

Expected: count remains 10.

- [ ] **Step 8: Run the project boundary check**

Run:

```powershell
$changed = git diff --name-only HEAD~5..HEAD
$forbidden = $changed | Where-Object {
    $_ -like 'backend/src/main/*' -or
    $_ -like 'frontend/src/*' -or
    $_ -eq 'backend/pom.xml'
}
if ($forbidden) { throw "Out-of-scope application changes: $($forbidden -join ', ')" }
```

Expected: exit code 0.

- [ ] **Step 9: Record final verification without committing generated state**

Run:

```powershell
git status --short
```

Expected: only unrelated pre-existing user changes may remain; Docker volumes, container state, and Elasticsearch data are not committed.
