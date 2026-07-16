# 模糊短输入与混合需求解析设计

**日期：** 2026-07-16
**状态：** 待用户最终审阅
**范围：** AI 导购对话中的短输入、条件补充、性别过滤与 LLM 兜底解析

## 1. 背景

当前系统已经具备性别感知推荐能力：Java 可以把男性、女性需求解析为 `targetGender`，商品候选 SQL 可以按 `male/female/unisex` 强过滤，Python 可以在 Java 候选范围内排序和解释。

现有问题是 Java 与 Python 对原始文本各自做一轮意图判断。用户只输入“男性”时，Java 能识别 `targetGender=male`，但 Python Router 会把它判断为 `unknown`；输入“男性穿搭”后 Python 才能命中推荐意图。类似问题还会出现在“女性”“黑色”“通勤”“500 以内”“宽松”等短输入上。

本设计采用已确认的 **方案 B + 方案 C**：

1. Java 使用确定性规则解析明确槽位，并维护权威的结构化 `DemandIntent`；
2. 当前输入如果只是条件补充，则与已有需求状态合并；
3. Java 无法可靠解析的复杂语义才交给 LLM 输出结构化补丁；
4. SQL 始终负责性别、分类、预算、季节等商品事实的硬过滤；
5. Python 只在 Java 候选池内排序、解释和生成自然追问。

## 2. 目标

- 第一次只输入“男性”或“女性”，也能立即展示对应商品候选；
- 单个短词可以作为新需求的起点，也可以作为上一轮需求的条件补丁；
- 男性与女性使用完全对称的解析、过滤和交互规则；
- 明确硬条件不依赖 LLM，保证稳定、低延迟、可测试；
- 模糊语义通过 LLM 补充解析，但 LLM 不能越过 Java/SQL 的商品事实边界；
- 信息不足时先展示已有条件下的候选，再自然追问，不阻塞浏览。

## 3. 非目标

- 不让 LLM 直接查询数据库或自行编造商品属性；
- 不根据商品模特图片推断商品适用性别；
- 不在本阶段构建通用自然语言理解平台；
- 不允许 Python 绕过 Java 候选池推荐被硬条件排除的商品；
- 不在本阶段处理“便宜一点”这类相对数值计算，除非上一轮存在可安全计算的明确预算。

## 4. 核心原则

### 4.1 结构化需求是权威事实

Java 产出的 `DemandIntent` 是本轮导购筛选的权威输入。Python 可以读取它，但不能从原始消息重新生成一套相互冲突的性别、分类或预算事实。

### 4.2 硬条件与软偏好分离

硬条件由 Java 归一化并由 SQL 执行：

- `targetGender`；
- `category`；
- `budgetMax`；
- `season`；
- 明确颜色或尺码（后续具备对应查询能力时）。

软偏好由 Python 在候选池内排序：

- 通勤、约会、校园等场景倾向；
- 成熟、休闲、硬朗、简约等风格；
- 显瘦、遮肉、显高、保暖等视觉或功能目标；
- 推荐理由与自然语言追问。

### 4.3 先确定性规则，后 LLM 兜底

明确表达不调用 LLM：

- 男性、男士、男款、男生；
- 女性、女士、女款、女生；
- 500 以内；
- 外套、衬衫、短裤、半裙；
- 夏季、冬季；
- 黑色、白色；
- 通勤、面试、约会。

只有规则无法可靠解释的表达才调用 LLM，例如：

- 给对象买；
- 偏硬朗一点；
- 想穿得成熟一些；
- 给家里长辈买；
- 不想太女性化；
- 类似韩剧男主穿的那种。

## 5. 第一轮短输入行为

第一次只输入“男性”时，不需要大模型判断性别。Java 直接建立新的需求状态：

```json
{
  "intent": "recommendation",
  "targetGender": "male",
  "category": null,
  "scene": [],
  "style": [],
  "hardFilters": ["targetGender"],
  "confidence": 1.0
}
```

随后 SQL 只允许 `male/unisex` 商品进入候选池。前端立即展示可售候选，同时助手回复：

> 已为你筛选男款和中性款商品。你想优先看上衣、外套、裤装，还是通勤、运动等场景？

第一次只输入“女性”时使用完全相同的流程，唯一差异是：

```json
{
  "targetGender": "female"
}
```

SQL 只允许 `female/unisex` 商品进入候选池，并立即展示候选后追问分类、场景或预算。

## 6. 后续短输入作为条件补丁

每轮输入先解析为 `DemandIntentPatch`，再与当前会话的权威需求状态合并。

示例：

```text
用户：推荐通勤外套
系统状态：scene=commute, category=外套

用户：男性
解析补丁：targetGender=male
合并状态：scene=commute, category=外套, targetGender=male

用户：黑色
解析补丁：preferredColors=[black]
合并状态：scene=commute, category=外套, targetGender=male, preferredColors=[black]

用户：500 以内
解析补丁：budgetMax=500
最终重新筛选并推荐
```

### 6.1 合并规则

- 新的单值硬条件覆盖旧值，例如 `male -> female`；
- 明确否定可以清除对应条件，例如“男女都可以”清除 `targetGender`；
- 多值偏好默认去重合并；
- 新分类覆盖旧分类，避免同时保留“外套”和“短裤”造成冲突；
- 用户明确说“重新推荐”“重新开始”时清空旧状态后建立新需求；
- 不明确的新输入不得静默删除已有硬条件。

## 7. 性别解析与冲突优先级

### 7.1 明确映射

| 用户表达 | 结构化结果 |
| --- | --- |
| 男性、男士、男款、男生 | `targetGender=male` |
| 女性、女士、女款、女生 | `targetGender=female` |
| 男生女生都可以、男女都行 | `targetGender=null` |
| 给男朋友/老公/爸爸买 | `targetGender=male` |
| 给女朋友/老婆/妈妈买 | `targetGender=female` |

### 7.2 来源优先级

```text
本轮明确购买对象
> 本轮明确性别表达
> 请求显式 gender
> 当前会话 DemandIntent
> 用户身体资料 gender
> 用户基础资料 gender
> 不过滤
```

例如登录用户档案为 `male`，但输入“给女朋友买一件通勤外套”，目标性别必须为 `female`。

### 7.3 冲突处理

- 同时出现明确男性和女性信号但无法判断购买对象时，不猜测，设置 `targetGender=null`；
- LLM 返回的性别与确定性规则冲突时，以确定性规则为准；
- LLM 低置信度时不把推断写入硬条件，改为询问用户；
- `unisex` 是商品事实值，不用作用户性别档案值。

## 8. B + C 混合解析流程

```mermaid
flowchart TD
    INPUT["用户输入"] --> RULE["Java 确定性 Slot Resolver"]
    RULE --> CONF{"是否得到可靠槽位或动作？"}
    CONF -->|"是"| PATCH["生成 DemandIntentPatch"]
    CONF -->|"否"| LLM["LLM 结构化解析兜底"]
    LLM --> VALIDATE["Java Schema / 枚举 / 置信度校验"]
    VALIDATE --> LOW{"置信度是否足够？"}
    LOW -->|"否"| ASK["询问用户确认"]
    LOW -->|"是"| PATCH
    PATCH --> MERGE["与会话 DemandIntent 合并"]
    MERGE --> SQL["SQL 硬过滤"]
    SQL --> RANK["Python 候选内排序与解释"]
    RANK --> UI["立即刷新商品 + 可选追问"]
```

## 9. LLM 结构化兜底契约

LLM 只能输出受限的补丁，不返回商品 ID，不直接决定 SQL，也不能修改确定性规则已经确认的事实。

```json
{
  "intent": "recommendation_refinement",
  "slots": {
    "targetGender": "male",
    "category": null,
    "scene": [],
    "style": ["mature"],
    "budgetMax": null,
    "preferredColors": [],
    "avoidColors": []
  },
  "confidence": 0.82,
  "evidence": ["老公", "成熟"],
  "needsClarification": false,
  "clarificationQuestion": null
}
```

Java 必须执行：

- JSON Schema 校验；
- 枚举归一化；
- 数值范围校验；
- evidence 必须能在本轮原文中找到或由明确上下文支持；
- 低于置信度阈值的硬条件不得进入 SQL；
- 超时、鉴权失败或非法响应时退回“不过度推断 + 询问用户”。

## 10. SQL 与商品数据边界

性别过滤继续采用商品事实属性：

```sql
AND EXISTS (
    SELECT 1
    FROM product_attribute gender_attr
    WHERE gender_attr.spu_id = p.id
      AND gender_attr.attr_name = '适用性别'
      AND gender_attr.attr_value IN (#{genderValue}, 'unisex')
)
```

规则：

```text
targetGender=male   -> male + unisex
targetGender=female -> female + unisex
targetGender=null   -> 不按性别过滤
```

当前迁移把半裙标为 `female`、部分牛津衬衫标为 `male`、其他大部分商品标为 `unisex`，数据精度不足。后续必须按真实商品定位补齐 `male/female/unisex`，不能根据模特图片猜测，也不能为了增加候选数量随意标记 `unisex`。

## 11. Python Router 调整方向

Python 不应再因为原始文本只有“男性”“女性”而返回 `unknown`。当 Java 已传入有效 `DemandIntent` 且包含推荐相关硬条件或软偏好时，Python 应将本轮视为可推荐或可细化需求：

```text
存在 targetGender/category/budgetMax/scene/style/attributes
-> recommendation 或 recommendation_refinement
```

Python 仍可识别库存、价格、尺码、售后等执行意图，但不得重新覆盖 Java 的 `targetGender/category/budgetMax`。

## 12. 前端交互

- 第一次输入一个有效短条件后立即刷新候选；
- 不要求用户补齐分类后才显示商品；
- 顶部线索区展示合并后的权威条件，而不是只展示最新一句原文；
- 条件不足时显示候选并追问，不显示“无法判断”；
- 明确区分“当前候选”和“AI 强匹配”：只有 Python 产生推荐引用时标记强匹配；
- 用户可以看到并清除已解析条件，避免错误状态持续污染后续对话。

## 13. 错误与降级

- 确定性规则和 SQL 不依赖 LLM；LLM 不可用时明确条件仍能刷新候选；
- RAG 不可用时可以继续使用 Java 商品候选，但推荐解释应说明当前未使用知识库增强；
- LLM 不可用且信息不足时，返回规则化追问，不把技术错误 JSON 直接展示给用户；
- Java 熔断打开时，前端仍保留当前候选和已解析条件；
- 任何降级都不能放宽性别、预算等已确认硬过滤。

## 14. 测试策略

### 14.1 Java 解析测试

- 第一轮“男性”产生 `targetGender=male` 和推荐意图；
- 第一轮“女性”产生 `targetGender=female` 和推荐意图；
- “给女朋友买”覆盖男性用户档案；
- “男女都可以”清除性别硬过滤；
- “男性 -> 黑色 -> 500 以内”正确合并条件；
- “换成女款”只覆盖性别，不丢失分类和场景；
- 低置信度 LLM 结果不进入硬过滤。

### 14.2 SQL 测试

- `male` 不返回 `female` 商品；
- `female` 不返回 `male` 商品；
- 两者都允许 `unisex`；
- `null` 不启用性别过滤；
- 性别与分类、预算、库存条件组合时仍正确。

### 14.3 Python 测试

- 原始文本为“男性”，但 Java `DemandIntent.targetGender=male` 时进入推荐流程；
- Python 不覆盖 Java 硬条件；
- 没有候选时不编造商品；
- RAG 不可用时仍能基于 Java 候选降级；
- 模糊表达只输出合法结构化补丁。

### 14.4 前端测试

- 第一轮输入“男性”后请求候选接口携带 `gender=male`；
- 候选立即刷新，同时显示补充分类/场景的提示；
- 后续短词保留并合并已有条件；
- 用户清除条件后请求不再携带该字段；
- 技术错误不以原始 JSON 形式展示。

## 15. 验收标准

- 输入“男性”立即展示 `male/unisex` 可售候选，并追问分类或场景；
- 输入“女性”立即展示 `female/unisex` 可售候选，并追问分类或场景；
- 输入“男性穿搭”与“男性”得到一致的性别硬过滤；
- 连续输入“通勤外套 -> 男性 -> 黑色 -> 500 以内”得到合并后的单一需求状态；
- 规则可确认的条件不调用 LLM；
- 复杂表达由 LLM 输出结构化补丁，经 Java 校验后才能进入筛选；
- Python、RAG 或 LLM 故障不会放宽 SQL 硬过滤，也不会让页面丢失当前候选；
- 商品性别数据完成一轮人工校准，明显女款不再默认标记为 `unisex`。
