# AI 推荐卡片与页面状态同步讨论稿

## 目标

这份文档记录 AI 推荐页目前需要补齐的能力，重点解决两个体验问题：

1. 用户在 AI 推荐里提问后，中间推荐卡片没有变成 AI 真正想推荐的商品。
2. 用户从 AI 推荐切到传统浏览，再切回 AI 推荐时，之前的聊天、推荐卡片和会话状态被刷新掉。

这是一份讨论稿，不是实施计划。目标是先确定数据流、状态归属和接口边界，再决定怎么改 Java、Python 和前端。

## 当前现象

当前 AI 推荐页有三列：

- 左侧：AI 聊天和筛选输入。
- 中间：推荐卡片，标题为“后端商品库候选”。
- 右侧：购物车摘要。

当前前端流程是：

```text
AiShoppingPage
-> 初始加载 api.recommendationCandidates({})
-> 把 setRecommendations 传给 ChatPanel
-> ChatPanel 调用 /api/assistant/chat/stream
-> ChatPanel 接收 done 或 recommendation 事件
-> ChatPanel 再调用 api.recommendationCandidates(requestFilters)
-> 如果有 recommendedSpuIds，就用它过滤候选
-> 更新中间推荐卡片
```

当前 Java 流程是：

```text
前端 /api/assistant/chat 或 /api/assistant/chat/stream
-> AssistantService
-> AssistantContextService
-> ProductCatalogService.findRecommendationCandidates(...)
-> Java 把 candidates 传给 Python /chat 或 /chat/stream
-> Java 把 Python product_refs 转成 recommendedSpuIds
-> 前端用 recommendedSpuIds 过滤候选商品
```

当前 Python 流程已经能接收 `candidates`，但面向 Java 的响应仍然基本是 `product_refs=[]`。这会导致 Java 返回空的 `recommendedSpuIds`，前端最后只能展示普通候选列表，而不是 AI 排序后的推荐结果。

## 问题一：推荐卡片没有反映 AI 回答

### 根因方向

中间推荐卡片目前不是直接绑定 Python 的推荐结果，而是绑定“重新查询 Java 候选商品 + 可选的 recommendedSpuIds 过滤”。

如果 Python 没有返回 `product_refs`，Java 就没有 `recommendedSpuIds`。这种情况下，前端会退回展示当前筛选条件下的全部候选商品，所以看起来像“AI 回答变了，但推荐卡片没变”。

### 建议排查证据

拿一次真实 AI 请求，按顺序看这些值：

```text
Java 发给 Python 的请求：
- candidates 数量
- candidates 里的 spu_id / sku_id

Python 返回：
- intent
- product_refs
- answer
- debug.intent_result
- debug.candidates

Java 响应或 SSE done：
- recommendedSpuIds
- candidatesCount

前端状态：
- ChatPanel 使用的 requestFilters
- api.recommendationCandidates(requestFilters) 返回的候选
- 前端过滤后的 recommendations
```

当前大概率会看到：

```text
debug.candidates 有商品
product_refs 为空
recommendedSpuIds 为空
前端显示当前筛选条件下的全部候选
```

## 问题二：尺码和复合意图识别不够稳

### 根因方向

当前 Python 侧的第一版意图识别主要依赖规则 router 和关键词。例如尺码相关问题依赖“尺码、穿什么码、合身、宽松”等关键词，身高体重信号依赖带单位的表达，例如 `175cm 70kg`、`175厘米 70公斤`、`130斤`。

这种方式稳定、可测试，但对真实用户输入不够友好。比如：

```text
明天面试想要显瘦 177 130 该怎么选
```

这句话同时包含：

- 场景：明天面试。
- 风格诉求：显瘦。
- 身高体重：177cm、130斤，也就是约 65kg。
- 推荐目标：挑合适商品。

如果只靠关键词和单位正则，系统可能无法把 `177 130` 归一化成 `height_cm=177`、`weight_kg=65`，也可能把问题拆错成单一尺码问题或单一风格问题。

### 建议排查证据

拿这类复合问题直接打 Python debug：

```text
明天面试想要显瘦 177 130 该怎么选
```

重点看：

```text
debug.intent_result
debug.missing_info_result
debug.tool_results.size_tool
debug.tool_results.size_tool.measurements
debug.selected_tools
debug.stop_reason
```

如果出现以下情况，就说明需要补语义解析层：

```text
intent 没有进入推荐或尺码相关路径
missing_info_gate 认为缺少身高体重
size_tool.measurements 里 height_cm 或 weight_jin 为空
answer 只回答风格，不做尺码约束
answer 只回答尺码，不处理面试/显瘦诉求
```

## 问题三：切换页面后 AI 状态丢失

### 根因方向

顶层 `App` 当前按 view 条件渲染页面：

```text
view === "ai" -> 挂载 AiShoppingPage
view === "browse" -> 卸载 AiShoppingPage
```

`AiShoppingPage` 自己保存 `recommendations`。`ChatPanel` 自己保存 `messages`、`draft`、`filters`、`threadId`、`isStreaming` 和 `error`。

用户切到传统浏览时，AI 页面组件会被卸载，这些局部 state 都会丢失。用户切回来时，组件重新挂载，于是聊天回到初始欢迎语，`threadId` 变回空，推荐卡片也重新加载初始候选。

### 建议排查证据

可以用 React DevTools 或临时日志确认：

```text
进入 AI 推荐时 AiShoppingPage mount
进入传统浏览时 AiShoppingPage unmount
回到 AI 推荐时 ChatPanel 的 useState 初始化再次执行
threadId 重新变成 undefined
messages 重新变成单条初始 assistant 消息
```

## 需要补齐的能力

### Python AI 服务

Python 需要真正实现候选商品推荐阶段，并把纯规则 router 升级成更适合真实对话的漏斗式推荐架构。

### 漏斗式推荐架构

建议新增一条面向推荐问题的主路径：

```text
LLM Router / Query Understanding
-> Missing Info Gate
-> Candidate Normalizer
-> Size Filter Node
-> Style Ranker
-> Answer Generator
-> Answer Validator
-> product_refs + answer
```

#### 1. LLM Router / Query Understanding

这个节点放在流程前端，负责把用户原话解析成结构化 JSON。它不直接决定商品事实，也不直接推荐商品，只负责理解用户输入。

示例输入：

```text
明天面试想要显瘦 177 130 该怎么选
```

示例输出：

```json
{
  "intent": "size_and_style_recommendation",
  "parameters": {
    "height_cm": 177,
    "weight_kg": 65,
    "raw_weight_value": 130,
    "raw_weight_unit": "jin",
    "style_requirements": ["面试", "显瘦"]
  },
  "confidence": 0.86,
  "needs_recommendation": true,
  "needs_size_filter": true
}
```

设计边界：

- LLM Router 可以做单位归一化，例如把 `177 130` 理解成 `177cm + 130斤`。
- LLM Router 必须返回固定 schema，解析失败时进入保守降级。
- LLM Router 不允许编造商品、价格、库存、SKU 或订单事实。
- 第一版可以保留规则 router 作为 fallback，避免 LLM 失败时整条链路不可用。

#### 2. Missing Info Gate

当推荐问题需要尺码约束，但 LLM Router 没提取出完整身高体重时，直接追问，不进入商品推荐。

示例：

```text
请补充一下您的身高体重，我才能先筛掉不合适尺码，再为您挑选最显瘦的款式。
```

#### 3. Candidate Normalizer

把 Java 传来的 `candidates` 统一成 Python 内部可排序结构。

需要保留：

```text
spu_id
sku_id
name
category
color
size
sale_price
stock_status
available_stock
material
fit_type
season
style_tags
main_image_url
```

这个节点只整理数据，不发明新商品。

#### 4. Size Filter Node

这是漏斗第一级硬过滤。它读取：

```text
state["candidates"]
state["query_understanding"].parameters.height_cm
state["query_understanding"].parameters.weight_kg
```

然后用确定性尺码规则匹配每个候选 SKU 或 SPU 的可穿尺码。

输出：

```text
state["filtered_candidates"]
state["rejected_candidates"]
state["size_filter_result"]
```

规则：

- 没有合适尺码的商品从推荐候选池剔除。
- 剔除原因要进入 debug，方便排查为什么某件商品没被推荐。
- 如果全部候选都被剔除，直接短路，不让大模型面对空列表继续编答案。

全部剔除时的回答：

```text
抱歉，看了下目前的候选款，暂时没有能同时匹配您身材和库存的商品。您可以放宽品类、颜色或预算，我再帮您重新筛。
```

#### 5. Style Ranker / Answer Generator

这是漏斗第二级软排序。它只接收已经通过尺码硬过滤的 `filtered_candidates`，再结合风格诉求排序。

输入示例：

```text
filtered_candidates: 3-5 件
style_requirements: ["面试", "显瘦"]
user_context: Java 组装的用户偏好
RAG evidence: 面试穿搭、显瘦颜色、版型建议
```

大模型任务：

- 在已保证尺码和库存基本可用的候选中选择最合适商品。
- 输出 `product_refs`，包含 `spu_id`、`sku_id`、`reason`、`rank_score`。
- 生成自然语言回答，解释为什么这些商品适合用户。

#### 6. Answer Validator

最终校验：

- `product_refs` 必须来自 `filtered_candidates`。
- 回答里提到的商品必须和 `product_refs` 一致。
- 如果没有候选证据，不能给具体商品推荐。

### 候选商品推荐阶段

```text
Java candidates
-> candidate normalizer
-> 基于 query、intent、user_context、尺码结果、RAG 证据进行 ranking
-> 输出 product_refs，包括 spu_id、sku_id、reason、rank_score
-> 最终 answer 要和 product_refs 指向同一批商品
```

规则：

- 不能返回 Java candidates 之外的商品引用。
- 没有 candidates 时，可以给泛化导购建议，但 `product_refs=[]`。
- candidates 存在但匹配弱时，可以返回低置信度少量推荐并说明原因，也可以明确说没有强匹配商品。
- 商品价格、库存、SKU、图片、品类等事实仍然来自 Java。
- 用户表达中的身高体重应进入结构化参数，不应只依赖原始自然语言正则。

### Java 后端

Java 需要把 Python 推荐结果保存成前端可用的结构：

```text
Python product_refs
-> 校验 refs 都来自 Java 本轮 candidates
-> 返回 recommendedSpuIds 或更丰富的推荐引用
-> 后续可持久化 assistant_recommendation 和 assistant_recommendation_item
```

当前 `recommendedSpuIds` 有一个不足：它只保留 SPU，不保留 SKU、推荐理由和排序分。更完整的兼容字段可以是：

```json
{
  "recommendedProducts": [
    {
      "spuId": 1002,
      "skuId": 2102,
      "reason": "符合通勤风格，L 码有库存，预算内。",
      "rankScore": 0.91
    }
  ]
}
```

这个字段可以兼容新增，同时保留现有 `recommendedSpuIds`。

### 前端

AI 页面需要一个高于条件渲染页面的状态拥有者，或者一个可恢复的会话状态存储。

最少需要保留：

```text
threadId
messages
filters
recommendations
AI 选中的推荐引用
最后一次 assistant 回答状态
streaming/error 状态
```

推荐卡片应从 AI 结果更新，而不是只从一次新的候选查询更新。

目标展示策略已经确定：

```text
AI 选中的商品排在上面
其他 Java 候选商品继续展示在下面
```

卡片应支持：

- AI 选中商品置顶。
- 非 AI 选中的候选商品保留在下方。
- AI 排序顺序稳定。
- SKU 级候选用于加购和购买。
- 如果有 Python reason，卡片上显示推荐理由。
- 如果 AI 没有选中任何商品，明确显示“暂无强匹配推荐”，同时仍可展示普通候选供用户浏览。
- AI 请求进行中时显示加载状态，避免旧卡片看起来像新结果。

## 设计选项

### 方案 A：把 AI 页面状态提升到 `App`

把 `messages`、`filters`、`threadId`、`recommendations`、`recommendedRefs` 移到 `App`，或者抽成一个由 `App` 持有的 `useAiShoppingState` hook。

优点：

- 实现相对直接。
- 因为 `App` 不会在切换页面时卸载，所以 AI 状态可以保留。
- 不要求立刻改 Java 会话恢复接口。

缺点：

- 浏览器整页刷新后仍会丢失状态。
- 如果直接塞进 `App`，`App` 会变重，最好抽成 hook。

### 方案 B：保持 AI 页面挂载，只用 CSS 隐藏

所有主页面都渲染，只隐藏非当前页面。

优点：

- 改动少。
- 组件内部 state 会自然保留。

缺点：

- 隐藏页面的 effect 可能继续运行。
- 可访问性和后台网络请求更难控制。
- 页面越来越多后维护性较差。

### 方案 C：以 Java 会话为准，进入页面时恢复

用 Java conversation API 作为长期状态来源：

```text
前端保存 threadId
-> GET /api/conversations/{threadId}/messages
-> 重建聊天消息
-> 再加载最近一次推荐结果
```

优点：

- 浏览器刷新、换设备后也能恢复。
- 符合 Java 负责会话持久化的边界。

缺点：

- 如果后端还没暴露最近推荐明细，需要补接口或扩展响应。
- 比第一阶段前端状态修复更重。

## 推荐推进顺序

建议分阶段做：

1. 先修前端状态：把 AI 页面状态提升到 `App` 持有的 hook，解决切换页面丢聊天的问题。
2. 再补 Python Query Understanding：增加 LLM Router 或结构化解析层，解决 `177 130`、复合意图、单位缺失问题。
3. 再补 Python 推荐漏斗：实现 `candidates -> size_filter -> style_ranker -> product_refs`，让 AI 真正选商品。
4. 再补 Java/前端契约：保留 `recommendedSpuIds`，兼容新增更丰富的推荐引用，例如 `recommendedProducts`。
5. 最后做持久恢复：页面刷新或打开历史会话时，从 Java conversation 和 recommendation 记录恢复聊天和推荐卡片。

这个顺序能先解决最明显的 UX 痛点，又不会把前端状态问题和 Python 推荐算法问题混在一起。

## 目标流程

```text
用户在 AI 页提问
-> 前端发送 message + threadId + filters 给 Java
-> Java 加载 user_context、chat_history、candidates
-> Python LLM Router 提取 intent、height_cm、weight_kg、style_requirements
-> Python 用尺码硬过滤剔除不适合候选
-> Python 用风格排序选择最合适商品并返回 product_refs
-> Java 校验 product_refs 来自本轮 candidates
-> Java done 事件返回推荐引用
-> 前端把 AI 选中商品排到卡片顶部
-> 前端把其他候选商品保留在下方
-> 用户切到传统浏览再回来，App 级 AI 状态仍保留
```

## 已确定的产品行为

推荐卡片区域采用混合展示：

```text
第一组：AI 为当前问题选中的商品
第二组：其他后端候选商品
```

建议 UI 文案：

```text
AI 推荐优先
其他可选商品
```

如果 AI 没有选中商品：

```text
AI 暂时没有选出强匹配商品，你可以继续浏览下面的后端候选。
```

## 还需要讨论的问题

1. 推荐卡片是否必须显示 Python 的 `reason`？
2. 第一阶段是否只要求“切换页面不丢状态”，还是连浏览器刷新也要恢复？
3. Java 响应下一步是新增 `recommendedProducts`，还是前端拿到 `spuId/skuId` 后再查商品详情？
4. LLM Router 第一版是否必须上线，还是先用增强正则支持 `177 130`，再升级 LLM Router？
5. Python 排序第一版要规则排序，还是规则排序加 LLM 解释？

## 第一阶段验收标准

前端状态修复：

- 用户在 AI 页提问。
- 用户切到传统浏览。
- 用户再切回 AI 推荐。
- 之前的消息、`threadId`、筛选条件和推荐卡片仍然存在。
- 初始候选加载不会覆盖 AI 推荐后的卡片。

推荐结果修复：

- Java 传入 candidates。
- Python 能把 `177 130` 这类表达归一化为身高体重。
- Python 能同时识别尺码和风格复合意图。
- Python 先按尺码过滤候选，再选出一个或多个 product_refs。
- Java 返回推荐引用。
- 前端把 AI 选中商品展示在上方。
- 前端把其他候选商品展示在下方。
- 如果 Python 没有返回 product_refs，前端明确说明没有强匹配，而不是让用户误以为普通候选就是 AI 推荐。

## 非目标

- 前端不能直接向 Python 伪造可信商品事实。
- Python 不能编造价格、库存、SKU id 或商品 id。
- 加购、下单、支付不能绕过 Java 鉴权和业务校验。
- 不替换传统浏览；传统浏览仍然是独立购物路径。
