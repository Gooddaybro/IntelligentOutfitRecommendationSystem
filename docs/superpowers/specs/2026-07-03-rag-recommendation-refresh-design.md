# RAG 推荐更新与数据边界设计

## 1. 背景

当前 AI 导购链路已经拆成两类数据源：

- Java 后端负责商品、SKU、价格、库存、上下架、商品属性、用户行为摘要和推荐候选池。
- Python AI 服务负责 LangGraph 编排、RAG 检索、推荐理由生成和自然语言表达。

最近排查发现，推荐意图如果过度依赖 RAG，会出现两个问题：

1. RAG 向量库缺失或未更新时，推荐回答会被错误兜底为“知识库没有资料”。
2. 如果把商品事实放进 RAG，商品价格、库存、SKU、上下架状态会和 Java 数据库产生不一致。

因此本设计选择方案 C：

```text
商品推荐候选永远来自 Java 数据库。
RAG 只补充推荐理由中的穿搭、颜色、材质、洗涤、场景解释。
```

第一阶段目标不是把 RAG 做成商品搜索引擎，而是把 RAG 从“可能污染商品事实的推荐源”调整为“可更新、可观测、可降级的解释知识源”。

## 2. 目标

第一版目标：

1. 固定数据边界：商品事实走 Java DB，解释性知识走 Python RAG。
2. 建立 RAG 知识库更新流程：编辑或上传知识文件后，可以重建向量库。
3. 增加 RAG 版本和更新时间可观测能力：调试信息能看出当前用的是哪一版知识。
4. 让推荐链路可降级：RAG 为空或过期时，商品推荐仍然能基于 Java candidates 正常返回。
5. 为后续自动化更新预留接口，但第一版不引入复杂数据同步平台。

## 3. 非目标

第一版不做以下内容：

- 不把 Java 商品表全量同步进 RAG。
- 不让 Python 直接读取或写入 Java 商品数据库。
- 不用 RAG 判断价格、库存、SKU、可购买尺码、上下架状态。
- 不引入 Kafka、向量数据库服务或后台 CMS。
- 不做复杂召回排序模型训练。
- 不改变现有 Java 推荐候选接口的事实源地位。

## 4. 四个设计方向

### 4.1 数据边界

推荐链路按数据强一致性拆分：

| 数据类型 | 事实源 | 使用位置 | 是否允许 RAG 回答 |
| --- | --- | --- | --- |
| 商品名称、SPU、SKU | Java DB | 推荐候选、推荐卡片、订单链路 | 否 |
| 价格、库存、上下架 | Java DB | 商品详情、库存、推荐候选过滤 | 否 |
| 商品属性标签 | Java DB | Python 推荐打分和理由拼接 | 否 |
| 用户画像、行为摘要 | Java DB | Java 构建 Python 请求上下文 | 否 |
| 颜色搭配原则 | Python RAG | 推荐理由补充、语义问答 | 是 |
| 洗涤养护知识 | Python RAG | 养护问答、商品解释 | 是 |
| 尺码解释知识 | Python RAG + 尺码工具 | 尺码建议补充说明 | 部分允许 |
| 场景穿搭建议 | Python RAG | 推荐理由补充 | 是 |

推荐回答的组装规则：

```text
Java candidates
-> Python build_product_refs 选择商品引用
-> RAG 返回可用解释 chunk
-> answer 组合商品事实和解释性知识
```

如果 RAG 没有 accepted chunks：

```text
仍然返回 Java candidates 支撑的 product_refs。
推荐理由只使用候选商品字段、用户偏好和行为摘要。
```

### 4.2 RAG 更新流程

当前 Python RAG 知识文件位于：

```text
AI-Clothing-Shopping-Assistant-System/clothing_assistant/data/
```

第一版保留轻量更新方式：

```text
编辑 txt 知识文件
-> 运行知识库上传/重建工具
-> 生成 simple_vector_store.json
-> Python RAG 读取新向量库
```

建议更新命令：

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
streamlit run clothing_assistant/ui/app_file_uploader.py
```

后续可以补一个命令行刷新入口，方便不用打开 Streamlit：

```bash
PYTHONPATH=. .venv/bin/python -m clothing_assistant.infrastructure.vector_store_rebuild
```

第一版知识文件建议拆成稳定主题：

```text
颜色选择.txt
洗涤养护.txt
尺码推荐.txt
场景穿搭.txt
材质说明.txt
```

更新规则：

- 商品具体价格、库存、SKU 不写入知识文件。
- 商品名称可以作为示例出现，但不能作为可购买事实。
- 每次更新知识文件后必须重建向量库。
- 重建失败时不能影响 Java 商品推荐，只影响解释补充。

### 4.3 版本与可观测

为了避免“不知道当前 RAG 用的是哪一版”，向量库重建时生成元数据文件：

```text
clothing_assistant/chroma_db/vector_store_meta.json
```

建议字段：

```json
{
  "version": "2026-07-03T18:30:00+08:00",
  "source_files": [
    {
      "file_name": "颜色选择.txt",
      "sha256": "source-file-hash",
      "updated_at": "2026-07-03T18:20:00+08:00"
    }
  ],
  "chunk_count": 42,
  "embedding_provider": "dashscope",
  "built_at": "2026-07-03T18:30:00+08:00"
}
```

Python `/chat` debug 中增加：

```json
{
  "rag_meta": {
    "version": "2026-07-03T18:30:00+08:00",
    "chunk_count": 42,
    "source_files": ["颜色选择.txt", "洗涤养护.txt"]
  }
}
```

推荐页面或开发调试时可以快速判断：

- RAG 是否被调用。
- RAG 是否返回 accepted chunks。
- 当前向量库版本是否落后。
- 当前回答是否由 Java candidates 独立支撑。

### 4.4 降级与验证

推荐链路必须满足：

```text
RAG 缺失、为空、弱证据、过期
!= 商品推荐不可用
```

推荐意图的降级策略：

| 场景 | 行为 |
| --- | --- |
| Java candidates 有数据，RAG 有 accepted chunks | 返回商品推荐，并补充 RAG 解释 |
| Java candidates 有数据，RAG 为空 | 返回商品推荐，只用 Java candidates 生成理由 |
| Java candidates 有数据，RAG 弱证据 | 拒绝弱证据，返回商品推荐 |
| Java candidates 为空，RAG 有解释知识 | 只给穿搭建议，不返回 product_refs |
| Java candidates 为空，RAG 也为空 | 追问或保守兜底 |

测试需要覆盖：

1. RAG 向量库缺失时，推荐仍返回 `product_refs`。
2. RAG 返回空 chunks 时，推荐不出现“知识库没有资料所以不能推荐”。
3. RAG 返回过时商品事实时，不能覆盖 Java candidate 的价格、库存和 SKU。
4. Debug 中能看到 RAG 版本、source files、accepted chunk 数。
5. Java 推荐候选接口仍然是推荐卡片的唯一商品来源。

## 5. 端到端数据流

```text
用户输入推荐需求
-> 前端 POST /api/assistant/chat/stream
-> Java AssistantContextService 查询 ProductCatalogService.findRecommendationCandidates
-> Java 把 candidates、用户画像、行为摘要传给 Python
-> Python LangGraph 判断 recommendation intent
-> Python 可选调用 RAG 获取解释性知识
-> Python build_product_refs 只从 Java candidates 里选商品
-> Java 校验 Python product_refs 必须存在于 candidates
-> 前端再调用 Java recommendation-candidates 获取推荐卡片
```

核心约束：

```text
前端展示的商品卡片来自 Java。
Python 返回的 product_refs 只是排序和理由引用。
RAG 不能新增商品，也不能覆盖商品事实。
```

## 6. 文件和模块影响

Java 项目：

| 模块 | 变化 |
| --- | --- |
| `AssistantContextService` | 继续负责候选商品、画像、行为摘要上下文 |
| `AssistantService` | 继续过滤 Python product_refs，避免 Python 编造商品 |
| `ProductCatalogService` | 继续作为推荐候选事实源 |
| `AssistantControllerTests` / `AssistantServiceTests` | 增加 RAG 降级场景契约测试 |

Python 项目：

| 模块 | 变化 |
| --- | --- |
| `tools/rag_tool.py` | 保持向量库缺失降级为空结果 |
| `agent/nodes.py` | 推荐意图允许在 RAG 空结果时基于 Java candidates 正常完成 |
| `application/recommendation_service.py` | 继续只从 Java candidates 生成 product_refs |
| `infrastructure/vector_store.py` | 增加 meta 读取/写入能力 |
| `api/app.py` / response debug | 可选输出 `rag_meta` |

## 7. 实施顺序

建议分三步做：

1. 固化推荐降级行为
   - 保证 RAG 空结果不阻断 Java candidates 推荐。
   - 增加 Python 单元测试。

2. 建立 RAG 元数据
   - 重建向量库时写 `vector_store_meta.json`。
   - RAG 查询时读取并写入 debug。

3. 建立更新文档和手动验收流程
   - 明确哪些内容能写进 RAG。
   - 增加“更新知识文件 -> 重建向量库 -> 接口验证”的操作文档。
   - 增加一个推荐问题和一个洗涤/颜色问题作为验收样例。

## 8. 验收标准

完成后应满足：

- AI 推荐商品卡片仍来自 Java DB。
- RAG 更新不需要改 Java 商品数据。
- RAG 缺失不会导致商品推荐不可用。
- Debug 能看到当前 RAG 版本和来源文件。
- 用户能按文档更新 RAG 知识并验证效果。
- 测试覆盖 Java candidates 与 RAG 解释知识的边界。

## 9. 后续演进

后续可以考虑：

- 增加后台管理页上传知识文件。
- 将 RAG meta 展示在开发调试台。
- 增加定时任务检查知识文件 hash 是否变化。
- 把政策知识改成结构化政策表，避免售后规则也出现过时问题。
- 做离线评测集，对比 RAG 更新前后的回答质量。
