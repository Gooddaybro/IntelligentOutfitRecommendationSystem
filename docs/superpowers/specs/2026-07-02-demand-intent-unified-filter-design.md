# DemandIntent 统一筛选契约设计

日期：2026-07-02

## 1. 文档目的

本文档设计 `DemandIntent` v1，用来解决 AI 穿搭推荐链路里的数据不对称问题：

```text
同一句用户需求
-> 前端猜一遍筛选条件
-> Java 猜一遍候选池条件
-> Python 又猜一遍排序偏好
-> 三边理解不一致，推荐结果就漂移
```

目标不是让三端各自实现“同一套逻辑”，而是让系统只有一个权威解析结果：

```text
用户自然语言 + 显式筛选
-> Java DemandIntentResolver
-> DemandIntent
-> Java 用它硬过滤 candidates
-> Python 用它候选内排序和解释
-> 前端只展示和透传，不再自行猜测
```

本次先写开发设计文档，不进入代码实现。用户确认后，再拆实现计划。

## 2. 本质问题

当前系统的问题不是某个关键词缺失，而是推荐链路缺少统一的需求理解契约。

已经出现的问题包括：

- 用户说“裙子”，数据库分类叫“半裙”，如果没有统一别名映射，就查不到或排不上。
- 用户说“女性通勤”，Java 能按性别过滤，但前端刷新右侧候选时可能没带 gender。
- Python Router 只命中一部分推荐词，导致“男性穿搭”“女生通勤半裙”可能被当成 unknown。
- Java、Python、前端都有局部解析逻辑，后续新增“年龄段、场景、风格、显瘦”等条件时会继续扩散。

根因：

```text
自然语言需求解析不是一个明确的系统边界。
```

只要三端都能自己推断筛选条件，数据不对称就无法彻底避免。

## 3. 设计原则

### 3.1 单一所有者

`DemandIntent` 的解析所有者是 Java 后端。

原因：

- Java 是商品事实、分类、价格、库存、上下架、用户画像和候选池的源头。
- 硬过滤条件必须影响 SQL、缓存 key 和候选池。
- Python 不能编造商品事实，也不应该决定哪些商品可以进入候选池。
- 前端不应该把展示层推断结果当成业务事实。

### 3.2 硬约束和软偏好分离

`DemandIntent` 必须区分两类信息：

```text
hardFilters：必须由 Java 执行，会影响候选池
softPreferences：由 Python 排序解释，不直接决定商品是否进入候选池
```

示例：

| 用户表达 | 类型 | 处理方 |
| --- | --- | --- |
| 女性 | hardFilter | Java |
| 半裙 | hardFilter | Java |
| 预算 500 以内 | hardFilter | Java |
| 有库存 | hardFilter | Java |
| 通勤 | softPreference，可升级为硬过滤 | Python 排序，Java 可作为候选收敛 |
| 显瘦 | softPreference | Python |
| 百搭 | softPreference | Python |
| 低饱和颜色 | softPreference | Python |

### 3.3 前端只展示，不解析

前端可以有 UI 筛选器，但它不能私自把自然语言解析成业务字段。

前端允许做：

- 发送用户原始 `message`。
- 发送显式 UI filter，例如用户选择“女性”“半裙”。
- 展示 Java 返回的 `resolvedIntent`。
- 使用 Java 返回的 `resolvedIntent` 刷新候选列表。

前端不允许做：

- 自己判断“裙子 = 半裙”。
- 自己判断“女性 = gender=female”并作为唯一事实来源。
- 自己维护一套和 Java/Python 重复的词表。

### 3.4 Python 只消费，不硬过滤

Python 可以使用 `DemandIntent` 排序和写推荐理由，但不能重新决定硬过滤。

Python 允许做：

- 根据 `softPreferences` 给候选商品加分。
- 使用 `hardFilters` 解释“为什么这些商品符合条件”。
- 在候选池内选择 `product_refs`。

Python 不允许做：

- 从候选池外编造商品。
- 重新解析性别、预算、分类并绕过 Java 候选池。
- 自己判断某个 Java 候选是否真实可买。

## 4. DemandIntent v1 契约

### 4.1 JSON 结构

```json
{
  "version": "v1",
  "source": "java_resolver",
  "rawQuery": "女性裙子推荐，适合上班通勤，预算500以内",
  "targetGender": "female",
  "category": "半裙",
  "scene": ["commute"],
  "style": ["minimal"],
  "budgetMax": 500,
  "attributes": ["显瘦", "高腰"],
  "hardFilters": ["targetGender", "category", "budgetMax"],
  "softPreferences": ["scene", "style", "attributes"],
  "confidence": 0.86,
  "missingSlots": []
}
```

### 4.2 字段说明

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `version` | string | 当前固定为 `v1` |
| `source` | string | 当前固定为 `java_resolver` |
| `rawQuery` | string | 用户原始需求，不参与 SQL |
| `targetGender` | string/null | `male/female/unisex/null` |
| `category` | string/null | Java 商品库标准分类名，如 `半裙`、`外套` |
| `scene` | string[] | 标准场景码，如 `commute/date/campus/daily` |
| `style` | string[] | 标准风格码，如 `minimal/casual/basic` |
| `budgetMax` | number/null | 预算上限 |
| `attributes` | string[] | 视觉或功能偏好，如 `显瘦`、`高腰` |
| `hardFilters` | string[] | Java 已经用于硬过滤的字段 |
| `softPreferences` | string[] | Python 可用于排序解释的字段 |
| `confidence` | number | 0 到 1，表示解析可信度 |
| `missingSlots` | string[] | 不足以推荐时可追问的字段 |

### 4.3 标准值白名单

第一版只支持少量稳定值，避免一上来做复杂语义平台。

```text
targetGender:
  male
  female
  unisex

category:
  T恤
  外套
  长裤
  衬衫
  卫衣
  针织衫
  西装
  羽绒服
  牛仔裤
  休闲裤
  短裤
  半裙

scene:
  commute
  date
  campus
  daily
  travel
  sport

style:
  commute
  minimal
  casual
  basic
  date
  warm
  sport

attributes:
  显高
  显瘦
  遮肉
  高腰
  中高腰
  垂顺
  挺括
  基础色
  低饱和
```

不在白名单里的值不能进入 `DemandIntent`。需要扩展时先改契约和测试。

## 5. 系统数据流

### 5.1 聊天推荐链路

```text
Frontend
  POST /api/assistant/chat
  message + explicitFilters

Java AssistantContextService
  DemandIntentResolver.resolve(message, explicitFilters, userProfile)
  -> DemandIntent
  -> RecommendationCandidateQuery
  -> ProductCatalogService.findRecommendationCandidates(query)

Java -> Python
  query
  user_context
  demand_intent
  candidates

Python
  不重新硬过滤
  用 demand_intent.softPreferences 给 candidates 打分
  返回 product_refs

Java
  校验 product_refs 必须来自 candidates
  返回 answer + recommendedItems + resolvedIntent

Frontend
  展示 resolvedIntent
  用 resolvedIntent 刷新候选列表
```

### 5.2 商品候选接口链路

`/api/products/recommendation-candidates` 不再只接受零散参数，也支持 `DemandIntent` 派生字段：

```text
GET /api/products/recommendation-candidates?category=半裙&gender=female&budgetMax=500
```

前端刷新候选时，参数必须来自 Java 返回的 `resolvedIntent`，而不是前端重新解析 message。

## 6. 组件设计

### 6.1 Java：DemandIntentResolver

新增一个 Java 服务：

```text
DemandIntentResolver
```

职责：

- 接收用户原始 message、显式请求筛选、用户画像。
- 输出唯一的 `DemandIntent`。
- 负责同义词归一，例如 `裙子 -> 半裙`。
- 负责目标性别归一，例如 `女生/女性/女朋友 -> female`。
- 负责预算解析，例如 `预算500以内 -> budgetMax=500`。
- 负责 confidence 和 missingSlots。

不负责：

- 商品排序。
- 自然语言回答。
- 复杂多轮意图推理。

### 6.2 Java：RecommendationCandidateQuery 只从 DemandIntent 构造

当前做法容易让调用方直接手拼 query。

目标做法：

```text
AssistantChatRequest
-> DemandIntent
-> RecommendationCandidateQuery
```

候选查询不再从 message、request filter、profile 到处取值。

### 6.3 Java：AssistantContext 增加 demandIntent

`AssistantContext` 增加：

```java
DemandIntent demandIntent
```

这样 Java 发给 Python 和返回给前端的都是同一个解析结果。

### 6.4 Python：schemas.py 增加 DemandIntent

Python API 请求增加：

```python
demand_intent: DemandIntent | None = None
```

Python 排序优先使用 `demand_intent`，不再把硬过滤解析散落在 Router、preference_parser、recommendation_service 里。

### 6.5 Frontend：显示 resolvedIntent

前端只负责：

- 展示 `resolvedIntent` 标签。
- 用 `resolvedIntent` 参数刷新候选列表。
- 如果用户显式选择筛选器，把筛选器作为 explicitFilters 传给 Java。

当前临时的 `inferRequestFiltersFromMessage` 后续应删除或只保留测试辅助，不再作为业务逻辑。

## 7. 如何杜绝数据不对称

### 7.1 契约文件先行

新增共享契约文件：

```text
docs/contracts/demand-intent-v1.md
```

或者后续迁入共享契约仓库：

```text
outfit-project-contract/contracts/demand-intent/v1.md
```

当前工作区没有 `outfit-project-contract` 目录，所以第一版先放在 Java 项目内。

### 7.2 Golden Cases

新增一份所有端共用的用例文件：

```text
docs/contracts/demand-intent-v1-cases.json
```

示例：

```json
[
  {
    "query": "女性裙子推荐",
    "expected": {
      "targetGender": "female",
      "category": "半裙",
      "hardFilters": ["targetGender", "category"]
    }
  },
  {
    "query": "男性穿搭",
    "expected": {
      "targetGender": "male",
      "category": null,
      "hardFilters": ["targetGender"]
    }
  },
  {
    "query": "女生上班通勤，预算500以内",
    "expected": {
      "targetGender": "female",
      "scene": ["commute"],
      "budgetMax": 500
    }
  }
]
```

Java 测试读取这份文件，验证 Resolver 输出。  
Python 测试读取这份文件，验证 schema 能接收并用于排序。  
前端测试读取这份文件，验证只展示，不重新解析。

### 7.3 禁止重复解析规则

代码规范写死：

```text
禁止在前端从 message 推断 gender/category/scene。
禁止在 Python 从 message 推断 hardFilters。
新增硬过滤字段必须先进入 DemandIntent。
```

这比“复制同一套规则到三端”更稳。

### 7.4 Contract Tests

新增测试层级：

```text
Java:
  DemandIntentResolverGoldenCaseTests
  AssistantContextServiceDemandIntentTests
  ProductCatalogServiceDemandIntentTests

Python:
  DemandIntentSchemaTests
  RecommendationServiceUsesDemandIntentTests

Frontend:
  ResolvedIntentDisplayTests
  ChatPanelDoesNotInferMessageFiltersTests
```

关键不是测试数量多，而是测试方向明确：

```text
Java 负责生成 DemandIntent
Python 负责消费 DemandIntent
Frontend 负责展示 DemandIntent
```

### 7.5 缓存 key 只看 DemandIntent 派生查询

所有候选缓存 key 必须由 `DemandIntent -> RecommendationCandidateQuery` 生成。

不能出现：

```text
聊天链路一个 key
商品候选接口另一个 key
前端推断一个 key
```

统一之后，同一个意图就有同一个候选池语义。

## 8. 实施阶段

### Phase 1：契约和 Java Resolver

范围：

- 新增 `DemandIntent` DTO。
- 新增 `DemandIntentResolver`。
- 新增 golden cases。
- `AssistantContextService` 使用 Resolver。
- `RecommendationCandidateQuery` 从 DemandIntent 构造。

不做：

- 不接 LLM。
- 不新增复杂配置中心。
- 不改商品主表。

### Phase 2：Java -> Python 契约打通

范围：

- `PythonChatRequest` 增加 `demand_intent`。
- Python `schemas.py` 增加 `DemandIntent`。
- Python `build_product_refs` 使用 `demand_intent` 排序。
- Python 不再重复解析 hard filters。

不做：

- 不让 Python 调 Java internal API 重查候选。
- 不让 Python 生成候选池外商品。

### Phase 3：前端改为展示 resolvedIntent

范围：

- `AssistantChatResponse` 增加 `resolvedIntent`。
- 前端展示已解析线索。
- 右侧候选刷新使用 `resolvedIntent` 派生参数。
- 删除前端自然语言推断逻辑。

不做：

- 不先做复杂筛选 UI。
- 不让前端维护词表。

### Phase 4：可选 LLM Mapper

当规则词典不够用时，再考虑接 LLM mapper。

边界：

- LLM 只产生候选 `DemandIntentDraft`。
- Java 校验白名单后才生成正式 `DemandIntent`。
- LLM 不能直接进 SQL。
- confidence 低时只做软偏好或追问。

## 9. 第一版解析规则

### 9.1 性别

```text
male:
  男、男生、男性、男士、男款、男朋友、爸爸、男友

female:
  女、女生、女性、女士、女款、女朋友、妈妈、女友
```

冲突处理：

```text
同时出现 male 和 female 信号 -> targetGender = null
```

### 9.2 分类

```text
裙子、半裙、半身裙、百褶裙、A字裙、直筒裙 -> 半裙
外套、夹克、风衣、西装外套、羽绒服 -> 对应标准分类
裤子、长裤、休闲裤、牛仔裤、短裤 -> 对应标准分类
衬衫、T恤、卫衣、针织衫 -> 对应标准分类
```

第一版只补高频词，不做全量服装知识图谱。

### 9.3 场景

```text
通勤、上班、办公室、职场、上班穿 -> commute
约会、见面、见男朋友、见女朋友 -> date
学生党、学生、大学生、校园、上课 -> campus
旅行、出差、旅游 -> travel
运动、健身、跑步 -> sport
日常、平时、周末 -> daily
```

### 9.4 属性

```text
显高、小个子、显腿长 -> 显高
显瘦、遮肉、不显胖、梨形、腿粗、胯宽 -> 显瘦/遮肉
高腰、中高腰 -> 高腰/中高腰
垂顺、有垂感 -> 垂顺
挺括、有型 -> 挺括
```

## 10. 验收标准

第一版完成后，必须满足：

- `女性裙子推荐` 解析为 `targetGender=female, category=半裙`。
- `男性穿搭` 解析为 `targetGender=male`，不推荐 female 商品。
- `女生上班通勤，预算500以内` 解析出 `targetGender=female, scene=commute, budgetMax=500`。
- Java 候选池只从 `DemandIntent` 生成查询条件。
- Python 请求能收到 `demand_intent`。
- Python 推荐理由能使用 `demand_intent` 中的偏好。
- 前端展示 Java 返回的 `resolvedIntent`。
- 前端不再从 message 推断 gender/category。
- golden cases 被 Java/Python/Frontend 至少各一组测试引用。

## 11. 风险和取舍

### 11.1 规则词典会增长

第一版规则不可避免会增长，但必须集中在 `DemandIntentResolver`，不能散在三端。

### 11.2 Java Resolver 不等于最终语义理解

Java 第一版可以是规则解析。等规则无法覆盖真实表达时，再引入 LLM mapper，但正式 `DemandIntent` 仍由 Java 校验后产出。

### 11.3 不追求一次做成通用 NLP

本项目当前目标是智能穿搭推荐，不需要先做通用自然语言理解平台。

第一版只支持穿搭推荐高频字段：

```text
gender
category
scene
style
budget
attributes
confidence
```

## 12. 不做事项

本阶段不做：

- 不抽独立微服务。
- 不引入新依赖。
- 不让前端和 Python 共享一份复制出来的词典。
- 不让 LLM 输出直接控制 SQL。
- 不重构全部推荐算法。
- 不改 `product_spu` 主表。

## 13. 待确认问题

开发前需要确认：

- `DemandIntentResolver` 是否确认由 Java 拥有？
- `scene/style/attributes` 第一版是否作为软偏好，暂不强过滤？
- 前端是否确认删除自然语言推断逻辑，改为展示 Java 返回的 `resolvedIntent`？
- Python 是否确认不再解析 hard filters，只消费 `demand_intent`？
- Golden cases 第一版是否先放在 Java 项目的 `docs/contracts/` 下，等共享契约仓库恢复后再迁移？

