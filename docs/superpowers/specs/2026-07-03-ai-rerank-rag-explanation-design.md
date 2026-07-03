# AI 语义重排序与 RAG 解释增强设计

## 1. 背景

当前 AI 导购链路已经拆成两类能力：

- Java 后端负责商品、SKU、价格、库存、上下架、商品属性、用户行为摘要和推荐候选池。
- Python AI 服务负责自然语言理解、偏好解析、候选商品重排序、推荐取舍、RAG 检索和自然语言解释。

如果把方案理解成“Java 推荐商品，Python 只补充几句 RAG 理由”，AI 的价值会很弱，系统会变成 AI 壳子。真正有意义的边界应该是：

```text
Java = 商品事实源 + 粗召回候选池
Python AI = 语义偏好解析 + 候选重排序 + 个性化取舍 + 推荐解释
RAG = 穿搭、颜色、材质、洗涤、场景知识补充
```

核心原则：

```text
AI 可以决定“Java 候选池里哪些商品更适合当前用户”。
AI 不可以发明 Java 没给的商品、价格、库存、SKU 或上下架事实。
```

本设计把原先“RAG 推荐更新”升级为“Java 候选池 + Python AI rerank + RAG explanation”的混合推荐方案。

## 2. 目标

第一版目标：

1. Java 继续提供可靠候选商品池，而不是把最终推荐完全定死。
2. Python 根据用户自然语言、用户画像、行为摘要和商品属性对候选池进行语义重排序。
3. Python 只从 Java candidates 中返回 `product_refs`，不能编造商品事实。
4. RAG 只为推荐理由补充穿搭、颜色、材质、洗涤、场景知识。
5. RAG 为空、弱证据或过期时，AI 推荐仍能基于 Java candidates 正常返回。
6. Debug 中能区分推荐依据：Java candidate、用户行为、语义偏好、RAG 解释。

## 3. 非目标

第一版不做以下内容：

- 不把 Java 商品表全量同步进 RAG。
- 不让 Python 直接读取或写入 Java 商品数据库。
- 不用 RAG 判断价格、库存、SKU、可购买尺码、上下架状态。
- 不引入 Kafka、向量数据库服务或后台 CMS。
- 不做复杂召回排序模型训练。
- 不改变现有 Java 推荐候选接口的事实源地位。
- 不让大模型直接输出最终可购买商品列表。
- 不把“推荐解释”误当作“商品事实源”。

## 4. 四个设计方向

### 4.1 数据边界

推荐链路按“事实可靠性”和“智能决策位置”拆分：

| 能力 | 负责方 | 说明 |
| --- | --- | --- |
| 商品事实源 | Java DB | SPU、SKU、价格、库存、上下架、商品属性 |
| 粗召回候选池 | Java `ProductCatalogService` | 根据类目、季节、风格、预算、性别等做可靠过滤 |
| 用户画像与行为摘要 | Java | 用户资料、近期点击、收藏、加购、购买、偏好类目和风格 |
| 语义偏好解析 | Python | 把“学生党、显瘦、通勤、不太成熟”转成排序信号 |
| 候选重排序 | Python | 在 Java candidates 内部打分、降权、选 top N |
| 推荐解释 | Python | 结合候选事实、用户偏好、行为摘要和 RAG 知识生成理由 |
| RAG 知识补充 | Python RAG | 颜色、材质、洗涤、场景穿搭、尺码解释 |

严格边界：

```text
Java 给什么商品，Python 才能推荐什么商品。
Python 可以改变候选商品排序，可以选择不推荐某些候选。
Python 不能新增候选外商品，不能覆盖价格、库存、SKU。
```

### 4.2 AI 语义重排序

Python AI 的主要价值放在 rerank，而不是简单润色。

输入：

| 输入 | 来源 | 用途 |
| --- | --- | --- |
| 用户原始问题 | 前端 -> Java -> Python | 提取自然语言偏好和约束 |
| Java candidates | Java DB | 可推荐商品池 |
| 用户画像 | Java | 性别、身高体重、偏好颜色、偏好风格 |
| 行为摘要 | Java | 近期兴趣、加购、购买、偏好类目和风格 |
| RAG chunks | Python RAG | 解释为什么某颜色、材质或场景更合适 |
| 尺码工具结果 | Python | 尺码相关排序和说明 |

推荐打分信号：

| 信号 | 示例 | 行为 |
| --- | --- | --- |
| 库存可售 | `stock_status=in_stock` | 加权 |
| 预算匹配 | `sale_price <= budgetMax` | 加权 |
| 场景匹配 | 通勤、约会、旅行、校园 | 加权 |
| 风格匹配 | minimal、casual、sport、formal | 加权 |
| 避雷偏好 | 不显胖、不太成熟、不正式 | 降权 |
| 行为兴趣 | 最近点击/收藏/加购同类商品 | 加权 |
| 购买历史 | 已买过相似商品 | 视场景加权或降权 |
| 尺码匹配 | 推荐尺码与 candidate size 一致 | 加权 |
| RAG 解释相关 | 有颜色/材质/场景知识支撑 | 只增强理由，不覆盖商品事实 |

输出：

```json
{
  "product_refs": [
    {
      "spu_id": 1002,
      "sku_id": 2101,
      "rank_score": 0.87,
      "reason": "符合通勤场景、预算 500 内、近期偏好外套，且基础色更容易搭配。"
    }
  ]
}
```

第一版可以继续使用规则版 `build_product_refs`，但文档上要把它定义为 AI rerank 模块。后续可以把规则解析和 LLM preference mapper 作为增强，不改变数据边界。

### 4.3 RAG 解释增强与更新

RAG 不是商品召回源，而是解释增强源。

适合放进 RAG：

| 知识类型 | 示例 |
| --- | --- |
| 颜色搭配 | 通勤更适合黑、灰、藏蓝、米白等基础色 |
| 材质说明 | 纯棉透气但易皱，聚酯更好打理 |
| 洗涤养护 | 牛仔、纯棉、羊毛、亚麻的清洗注意点 |
| 场景穿搭 | 校园、通勤、约会、旅行、轻户外的搭配原则 |
| 尺码解释 | 宽松、合身、修身版型如何影响尺码选择 |

不适合放进 RAG：

| 数据类型 | 原因 |
| --- | --- |
| 商品价格 | 变化频繁，必须来自 Java DB |
| 库存 | 变化频繁，必须来自 Java DB |
| SKU | 结构化标识，不能语义猜测 |
| 上下架 | 直接影响可购买性 |
| 用户行为 | 用户隐私和强一致上下文，来自 Java |

更新规则：

- 商品具体价格、库存、SKU 不写入知识文件。
- 商品名称可以作为示例出现，但不能作为可购买事实。
- 每次更新知识文件后必须重建向量库。
- 重建失败时不能影响 AI rerank，只影响解释丰富度。

建议保留当前轻量更新方式：

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
streamlit run clothing_assistant/ui/app_file_uploader.py
```

后续增加命令行刷新入口：

```bash
PYTHONPATH=. .venv/bin/python -m clothing_assistant.infrastructure.vector_store_rebuild
```

### 4.4 可观测、降级与验证

推荐 debug 需要能解释“AI 到底做了什么”，否则用户会感觉只是壳子。

建议 debug 增加：

| 字段 | 含义 |
| --- | --- |
| `semantic_preferences` | 用户语义偏好解析结果 |
| `candidate_scores` | 每个候选商品的排序分和得分原因 |
| `selected_product_refs` | 最终推荐商品引用 |
| `rerank_reason` | 为什么这些商品排在前面 |
| `rag_meta` | RAG 版本、chunk 数、来源文件 |
| `accepted_chunks` | 被接受的 RAG 解释证据 |
| `rejected_chunks` | 被拒绝的弱证据 |
| `recommendation_source` | `java_candidates_with_ai_rerank` |

RAG 元数据建议：

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

降级策略：

| 场景 | 行为 |
| --- | --- |
| Java candidates 有数据，RAG 有 accepted chunks | AI rerank，并用 RAG 增强解释 |
| Java candidates 有数据，RAG 为空 | AI rerank，理由只使用候选字段、画像和行为摘要 |
| Java candidates 有数据，RAG 弱证据 | 拒绝弱证据，继续 AI rerank |
| Java candidates 为空，RAG 有解释知识 | 只给穿搭建议或追问，不返回 `product_refs` |
| Java candidates 为空，RAG 也为空 | 追问预算、场景、类目或偏好 |
| Python AI 服务异常 | Java 返回可控错误或候选兜底，不能编造 AI 推荐 |

## 5. 端到端数据流

```text
用户输入推荐需求
-> 前端 POST /api/assistant/chat/stream
-> Java AssistantContextService 构建上下文
-> Java ProductCatalogService 查询粗召回 candidates
-> Java 行为摘要进入 user_context
-> Python LangGraph 判断 recommendation intent
-> Python preference parser 提取语义偏好
-> Python reranker 对 Java candidates 打分、排序、取舍
-> Python 可选调用 RAG 获取解释性知识
-> Python 返回 product_refs + reason + debug
-> Java 校验 product_refs 必须存在于 candidates
-> 前端展示 Java recommendation-candidates 卡片，并附加 AI reason
```

关键约束：

```text
Java 决定哪些商品可以被推荐。
Python 决定这些候选商品里哪些更适合当前用户。
RAG 解释为什么这种颜色、材质、场景更合理。
```

## 6. 关键模块设计

### 6.1 Java 粗召回

模块：

```text
ProductCatalogService.findRecommendationCandidates(query)
AssistantContextService.buildContext(...)
AssistantService.toPythonRequest(...)
```

职责：

- 根据类目、风格、季节、材质、版型、预算、性别做可靠过滤。
- 把候选商品字段转成 Python `candidates`。
- 把行为摘要转成 Python `user_context`。
- 接收 Python `product_refs` 后做二次校验。

### 6.2 Python 语义重排序

模块：

```text
application.preference_parser
application.recommendation_service
agent.nodes
```

职责：

- 解析用户自然语言偏好。
- 读取用户画像和行为摘要。
- 对 candidates 计算 `rank_score`。
- 输出 top N `product_refs`。
- 为每个推荐生成可解释 reason。

第一版排序仍以确定性规则为主，避免大模型输出不可控：

```text
rules first
optional LLM preference mapper
candidate-bound product_refs
```

### 6.3 RAG 解释增强

模块：

```text
tools.rag_tool
infrastructure.vector_store
retrieval_grader
answer_generator
```

职责：

- 检索解释性知识。
- 通过 `retrieval_grader` 拒绝弱证据。
- 在推荐理由中补充颜色、材质、场景说明。
- 向 debug 暴露 RAG 版本和来源。

## 7. 验收标准

测试需要覆盖：

1. Python 只从 Java candidates 返回 `product_refs`。
2. 用户说“学生党、预算 300、显瘦、通勤”时，Python rerank 能优先匹配候选属性。
3. 行为摘要能影响排序，例如近期多次点击外套时外套候选加权。
4. RAG 缺失或为空时，AI 推荐仍返回 `product_refs`。
5. RAG 返回过时商品事实时，不能覆盖 Java candidate 的价格、库存和 SKU。
6. Java 过滤掉 Python 返回的候选外商品引用。
7. Debug 能看见排序原因、RAG 状态和推荐来源。

人工验收问题：

```text
推荐一件 300 以内适合学生党通勤、不要太正式的外套。
```

期望：

```text
Java 返回候选池。
Python 选择更偏休闲、预算匹配、通勤可用的候选。
推荐理由说明预算、场景、风格和行为偏好。
如果 RAG 可用，再补充基础色或材质搭配解释。
```

## 8. 实施顺序

建议分四步做：

1. 固化 AI rerank 边界
   - `build_product_refs` 明确作为 candidate reranker。
   - Debug 增加 candidate score 和推荐来源。
   - 测试候选外商品不能进入推荐结果。

2. 强化语义偏好解析
   - 完善“学生党、显瘦、通勤、不太成熟、平价、百搭”等偏好词。
   - 将解析结果写入 debug。
   - 保持 LLM mapper 可选，不作为第一版强依赖。

3. 接入 RAG 解释增强
   - 推荐理由可引用 accepted RAG chunks。
   - RAG 空结果不阻断推荐。
   - 增加 RAG meta。

4. 建立验收和更新文档
   - 明确 RAG 知识文件可写内容和禁写内容。
   - 提供重建向量库命令。
   - 增加端到端推荐验收样例。

## 9. 文件和模块影响

Java 项目：

| 模块 | 变化 |
| --- | --- |
| `ProductCatalogService` | 明确为粗召回候选池，不承担最终智能排序 |
| `AssistantContextService` | 继续组装候选商品、画像、行为摘要 |
| `AssistantService` | 校验 Python `product_refs` 必须来自 candidates |
| `AssistantServiceTests` | 增加候选外引用过滤、行为摘要传递、推荐排序契约 |
| `ProductCatalogServiceTests` | 保证候选池查询仍按可靠事实过滤 |

Python 项目：

| 模块 | 变化 |
| --- | --- |
| `application.preference_parser` | 强化模糊偏好解析 |
| `application.recommendation_service` | 定义为 AI rerank 规则核心 |
| `agent.nodes` | 推荐意图在 RAG 空结果时仍完成 rerank |
| `tools.rag_tool.py` | 保持向量库缺失降级为空结果 |
| `infrastructure.vector_store` | 增加 RAG meta 读取/写入 |
| API debug | 输出偏好解析、候选得分、RAG meta |

## 10. 后续演进

后续可以考虑：

- 把规则版 rerank 演进成“规则 + LLM preference mapper + 学习到的行为权重”。
- 增加离线评测集，对比 rerank 前后的点击和加购表现。
- 将 RAG meta 展示在 Python 调试台和 Java debug 响应里。
- 增加后台管理页上传 RAG 知识文件。
- 把政策知识改成结构化政策表，避免售后规则也出现过时问题。
- 后续如果商品量变大，可以增加独立向量召回，但召回结果仍必须回查 Java DB 校验可售事实。
