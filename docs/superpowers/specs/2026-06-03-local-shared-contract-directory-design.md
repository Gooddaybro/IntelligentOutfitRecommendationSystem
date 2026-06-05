# 本地共享契约目录设计

## 目的

本文用于定义 Java 后端项目和 Python AI 项目如何在本地共同遵循同一套业务边界、接口契约和开发检查规则。

目标不是先实现流式接口，而是先建立一个双方都能引用的共享契约来源。后续 Java 和 Python 开发流式对话接口时，都必须以这份共享契约为准，避免两边各写一套规则后出现字段、职责和业务事实不一致。

## 背景

当前系统被拆成两个项目：

- Java 后端项目负责真实业务系统，包括用户、会话、商品、SPU、SKU、价格、库存、订单、支付和接口网关。
- Python AI 项目负责意图识别、推荐编排、RAG 问答和自然语言生成。

SSE 流式对话接口会同时影响两个项目：

- 前端调用 Java 的流式接口。
- Java 装配上下文后调用 Python 的流式接口。
- Python 持续生成 token。
- Java 接收 Python token 后转发给前端，并在 done 事件后完成会话落库。

因此，契约文档不适合只放在 Java 项目或只放在 Python 项目中。更稳妥的方式是建立一个本地共享契约目录，让两个项目都通过相对路径引用同一份文档。

## 推荐本地目录结构

短期采用本地并列目录，不立即引入 Git submodule。

推荐结构：

```text
D:\git\
├── outfit-project-contract\
├── Intelligent Outfit Recommendation System\
└── AI Clothing Shopping Assistant System\
```

说明：

- `outfit-project-contract` 是共享契约目录。
- `Intelligent Outfit Recommendation System` 是当前 Java 后端项目。
- `AI Clothing Shopping Assistant System` 是 Python AI 项目目录。
- Java 和 Python 项目都通过 `..\outfit-project-contract` 引用共享契约。

这样做的好处是简单直接，不需要一开始处理 submodule、远程仓库权限和 CI 初始化问题。

## 共享契约目录结构

第一版共享契约目录建议只包含必要文件，不追求复杂。

```text
outfit-project-contract\
├── AGENTS.md
├── docs\
│   ├── business-rules.md
│   ├── coding-boundary.md
│   └── dev-checklist.md
└── contracts\
    └── assistant-streaming-chat\
        ├── v1.md
        ├── examples\
        │   ├── frontend-java-request.json
        │   ├── java-python-request.json
        │   ├── meta.event
        │   ├── token.event
        │   ├── done.event
        │   └── error.event
        └── schemas\
            ├── assistant-chat-request.schema.json
            ├── python-chat-request.schema.json
            ├── sse-meta.schema.json
            ├── sse-token.schema.json
            ├── sse-done.schema.json
            └── sse-error.schema.json
```

### AGENTS.md

共享总规则，面向 AI coding agent 和人工开发者。

需要说明：

- 开发前必须阅读哪些共享文档。
- Java 和 Python 的职责边界。
- 哪些业务事实必须以 Java 为准。
- 契约变更必须先改共享文档，再改两边实现。

### docs/business-rules.md

业务规则文档。

第一版重点写：

- Java 是真实业务数据源。
- Python 不编造商品价格、库存、订单状态。
- 商品推荐可以先到 SPU。
- 下单、锁库存、支付必须落到 SKU。
- 库存、价格、订单、支付状态以 Java 数据库和 Java API 为准。
- Python 只能基于 Java 提供的候选商品和上下文生成推荐解释。

### docs/coding-boundary.md

工程边界文档。

第一版重点写：

- Java 负责鉴权、会话归属校验、上下文装配、候选商品查询、落库和对外 API。
- Python 负责意图识别、排序解释、自然语言回答和流式 token 输出。
- 前端不能直接提交 `user_context`、`candidates`、价格、库存或订单状态。
- Java 不能信任 Python 返回的业务事实，只能把 Python 返回内容视为推荐解释和商品引用。

### docs/dev-checklist.md

开发前检查清单。

第一版重点写：

- 是否读过共享契约。
- 是否确认当前改动属于 Java 边界还是 Python 边界。
- 是否确认请求体和响应体符合契约。
- 是否新增或更新了 schema 示例。
- 是否更新了两边测试。
- 是否保留旧同步接口用于回退。

### contracts/assistant-streaming-chat/v1.md

流式对话接口的主契约。

第一版重点写：

- 前端到 Java 继续使用 `AssistantChatRequest`。
- Java 到 Python 使用 `PythonChatRequest`。
- Python 到 Java 使用 `token`、`done`、`error` 事件。
- Java 到前端使用 `meta`、`token`、`done`、`error` 事件。
- SSE `data` 统一使用单行 JSON。
- Java 在流开始前保存用户消息。
- Java 在收到 Python `done` 后保存 assistant 消息。
- 前端断开时 Java 正常结束流，不打印 error 级别日志。

## Java 项目如何引用共享契约

当前 Java 项目的 `AGENTS.md` 后续可以增加一段共享契约引用。

建议内容：

```md
## Shared Contract

开发 assistant、Java-Python API、SSE 流式接口、推荐链路或跨服务数据契约前，必须先阅读：

- ..\outfit-project-contract\AGENTS.md
- ..\outfit-project-contract\docs\business-rules.md
- ..\outfit-project-contract\docs\coding-boundary.md
- ..\outfit-project-contract\docs\dev-checklist.md
- ..\outfit-project-contract\contracts\assistant-streaming-chat\v1.md

Java 后端是真实业务数据源，负责用户、会话、商品、SKU、价格、库存、订单、支付和前端 API。
不得让前端或 Python 绕过 Java 伪造 user_context、candidates、价格、库存或订单状态。
```

这段后续只作为 Java 项目的入口规则。真正的共享规则仍然写在 `outfit-project-contract` 中。

## Python 项目如何引用共享契约

Python 项目的 `AGENTS.md` 后续可以增加类似内容。

建议内容：

```md
## Shared Contract

开发 AI 对话、推荐、RAG、SSE 输出或 Java-Python API 调用前，必须先阅读：

- ..\outfit-project-contract\AGENTS.md
- ..\outfit-project-contract\docs\business-rules.md
- ..\outfit-project-contract\docs\coding-boundary.md
- ..\outfit-project-contract\docs\dev-checklist.md
- ..\outfit-project-contract\contracts\assistant-streaming-chat\v1.md

Python AI 项目只负责意图识别、推荐编排、RAG 问答和自然语言生成。
商品价格、库存、订单状态、支付状态和用户会话归属必须以 Java API 返回为准。
不得在 Python 侧编造或持久化真实业务事实。
```

## 流式对话契约边界

共享契约中需要明确两层请求体不同。

### 前端到 Java

前端请求 Java 时继续使用 Java 的轻量请求：

```json
{
  "threadId": "th_xxx",
  "message": "我身高175体重70kg，适合穿什么码？",
  "category": "outerwear",
  "style": "commute",
  "season": "autumn",
  "fit": "regular",
  "budgetMax": 800
}
```

前端不得传：

- `user_context`
- `candidates`
- `chat_history`
- 商品价格
- 库存数量
- 订单状态

### Java 到 Python

Java 调用 Python 时使用 Java 装配后的完整请求：

```json
{
  "request_id": "req_xxx",
  "session_id": "th_xxx",
  "thread_id": "th_xxx",
  "query": "我身高175体重70kg，适合穿什么码？",
  "chat_history": [],
  "user_context": {
    "user_id": 10001,
    "height_cm": 175,
    "weight_kg": 70
  },
  "candidates": [],
  "debug": false
}
```

这层请求只能由 Java 生成，不允许前端直传。

### Python 到 Java

Python 返回 SSE 事件：

```text
event: token
data: {"content":"我建议"}

event: done
data: {"request_id":"req_xxx","answer":"我建议您穿 L 码。","intent":"size_recommendation","product_refs":[]}

event: error
data: {"code":"internal_error","message":"大模型生成异常"}
```

### Java 到前端

Java 转发给前端时可以增加 `meta` 事件，并把最终 `done` 转成前端友好的结构：

```text
event: meta
data: {"request_id":"req_xxx","thread_id":"th_xxx"}

event: token
data: {"content":"我建议"}

event: done
data: {"thread_id":"th_xxx","answer":"我建议您穿 L 码。","recommended_spu_ids":[],"candidates_count":12,"intent":"size_recommendation"}

event: error
data: {"code":"internal_error","message":"大模型生成异常"}
```

## 约束机制

共享契约需要同时约束 AI、人工开发和自动化测试。

### 文档约束

两个项目的 `AGENTS.md` 都引用同一个本地共享契约目录。

约束目标：

- AI coding agent 进入任一项目后，都能先读同一份规则。
- 人工开发者不需要在 Java 和 Python 文档之间判断哪个更新。
- 跨项目设计变更先改共享契约，再改实现。

### Schema 约束

流式接口中关键 JSON 结构需要放入 `schemas` 目录。

Java 项目后续测试应校验：

- 前端请求体不接受 `user_context` 和 `candidates`。
- Java 发给 Python 的请求符合 `python-chat-request.schema.json`。
- Java 发给前端的 `meta`、`token`、`done`、`error` data 符合对应 schema。

Python 项目后续测试应校验：

- `/chat/stream` 接收的请求符合 `python-chat-request.schema.json`。
- Python 输出的 `token`、`done`、`error` data 符合对应 schema。
- SSE `data` 始终是单行 JSON。

### 版本约束

第一版契约路径使用：

```text
contracts\assistant-streaming-chat\v1.md
```

规则：

- 兼容性字段新增可以继续放在 v1。
- 删除字段、改字段含义、改事件类型、改职责边界，需要新建 v2。
- Java 和 Python 必须在各自项目中明确当前遵循的契约版本。

### CI 约束

本地共享目录阶段，CI 不一定能直接拿到 `..\outfit-project-contract`，因此第一阶段先以本地开发约束为主。

等契约稳定后，将 `outfit-project-contract` 独立为 Git 仓库，再用 Git submodule 挂到 Java 和 Python 项目：

```text
java-backend\shared-contract
AI Clothing Shopping Assistant System\shared-contract
```

进入 submodule 阶段后，CI 可以强制校验：

- `shared-contract` 目录存在。
- 当前项目引用的契约版本存在。
- schema 校验测试通过。
- 关键 API 测试覆盖契约示例。

## 推荐落地顺序

### 阶段一：只建立本地共享契约目录

目标：先让两边有同一份可读文档。

动作：

1. 在 `D:\git` 下创建 `outfit-project-contract`。
2. 创建共享 `AGENTS.md`。
3. 创建 `docs\business-rules.md`。
4. 创建 `docs\coding-boundary.md`。
5. 创建 `docs\dev-checklist.md`。
6. 创建 `contracts\assistant-streaming-chat\v1.md`。
7. 先放入示例，不急着补全所有 schema。

### 阶段二：让 Java 和 Python 项目引用共享契约

目标：AI 和人工开发进入任一项目时，都能被指向同一份规则。

动作：

1. 更新 Java 项目的 `AGENTS.md`，增加 `..\outfit-project-contract` 引用。
2. 更新 Python 项目的 `AGENTS.md`，增加 `..\outfit-project-contract` 引用。
3. 保留两个项目自己的语言、框架和测试规则。
4. 不把共享契约内容复制进两个项目。

### 阶段三：补充 schema 和测试约束

目标：从“读文档遵守”升级为“测试自动发现偏离”。

动作：

1. 为 Java 前端请求体补充 schema。
2. 为 Java-Python 请求体补充 schema。
3. 为 SSE 事件 data 补充 schema。
4. Java 测试引用共享 schema。
5. Python 测试引用共享 schema。

### 阶段四：迁移为 Git submodule

目标：让共享契约可以进入 GitHub 和 CI。

动作：

1. 将 `outfit-project-contract` 初始化为独立 Git 仓库。
2. 推送到远程仓库。
3. Java 项目用 `shared-contract` submodule 引用它。
4. Python 项目用 `shared-contract` submodule 引用它。
5. CI 初始化 submodule 并运行 schema 校验。

## 暂不做的事情

第一阶段暂不做：

- 不改 Java SSE 代码。
- 不改 Python SSE 代码。
- 不立刻引入 Git submodule。
- 不把共享文档复制到两个项目中。
- 不要求 CI 读取本地 `..\outfit-project-contract`。
- 不把前端请求体改成 `PythonChatRequest`。

## 审批点

开始创建共享契约目录前，需要确认：

- 本地共享目录名称使用 `outfit-project-contract`。
- 本地共享目录放在 `D:\git\outfit-project-contract`。
- Java 项目通过 `..\outfit-project-contract` 引用共享契约。
- Python 项目也通过 `..\outfit-project-contract` 引用共享契约。
- 第一版只写必要文档和流式接口契约，不先做 submodule。
- 后续实现流式接口前，Java 和 Python 都必须先遵循 `contracts\assistant-streaming-chat\v1.md`。

确认后，下一步可以先创建本地共享目录和第一版共享文档，再进入 Java/Python 流式接口开发。
