# 推荐 ES 召回评测清单

日期：2026-08-07

## 目的

这份清单用于判断“推荐候选接入 ES 召回”是否真的提升自然语言商品发现能力。不要先调分词器、同义词或字段权重；先用固定查询记录结果，再根据证据优化。

## 前置条件

1. 启动 MySQL、Redis、Elasticsearch。
2. 启动 Java 后端，并打开：

```bash
APP_ELASTICSEARCH_ENABLED=true \
APP_RECOMMENDATION_ES_RECALL_ENABLED=true \
sh ./mvnw spring-boot:run
```

3. 重建商品索引：

```bash
curl -X POST \
  -H 'X-Internal-Token: dev-internal-token' \
  http://localhost:8080/internal/search/products/rebuild
```

4. 确认索引别名存在：

```bash
curl http://localhost:9200/product_current/_count
```

## 固定查询集

| 编号 | 用户输入 | 期望观察点 | ES 命中数 | 最终候选数 | Python 前 3 个商品 |
|---|---|---|---:|---:|---|
| 1 | 通勤半裙 | 场景和类目都应影响召回 |  |  |  |
| 2 | 秋天显瘦针织 | 季节、视觉效果、材质语义 |  |  |  |
| 3 | 约会连衣裙 | 场景词能否命中描述/风格 |  |  |  |
| 4 | 运动休闲外套 | 多风格词组合 |  |  |  |
| 5 | 职场衬衫 | 场景同义表达是否可用 |  |  |  |
| 6 | 梨形身材显瘦 | 身材诉求是否被属性/描述覆盖 |  |  |  |
| 7 | 夏天透气 | 季节和材质/描述相关性 |  |  |  |
| 8 | 黑色简约 | 颜色不在 SPU 搜索文档中，观察是否需要 SKU 搜索 |  |  |  |

## 记录方式

每条查询记录三类数据：

1. ES SPU 命中数量。
2. Java 最终传给 Python 的 SKU 候选数量。
3. Python 排序后的前 3 个商品引用。

同时观察 Prometheus 指标：

- `app_recommendation_recall_requests_total{engine="elasticsearch",outcome="success"}`
- `app_recommendation_recall_requests_total{engine="elasticsearch",outcome="empty"}`
- `app_recommendation_recall_requests_total{engine="mysql",outcome="fallback"}`
- `app_recommendation_recall_spu_hits`
- `app_recommendation_recall_candidates`

## 判定规则

- 如果 ES 命中为空但 MySQL 有大量候选，先检查 Mapping 字段是否覆盖该词，不直接加同义词。
- 如果 ES 命中多但最终候选少，优先检查 MySQL 硬过滤是否过严。
- 如果 Python 前 3 个结果明显不合理，优先检查 Python 排序解释，不把排序问题伪装成 ES 召回问题。
- 如果“黑色简约”这类 SKU 属性查询表现差，再考虑是否需要 SKU 级搜索文档；第一阶段不直接新建 SKU 索引。
