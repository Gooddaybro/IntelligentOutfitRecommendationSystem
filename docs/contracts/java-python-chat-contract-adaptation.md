# Java-Python Chat Contract Adaptation

本文档记录 Java 后端适配 Python AI `/chat` 接口 snake_case JSON 契约的开发设计。当前 Java 侧已按本文完成 `/chat` 请求 DTO、响应 DTO 和转换逻辑适配。

## 1. 背景

Python AI 服务使用 Pydantic 接收请求，跨服务 JSON 字段以 snake_case 为准。Java 后端内部仍使用 Java 标准驼峰命名，但发送给 Python 的请求体必须稳定输出 snake_case 字段，避免 Python 返回 422 参数校验错误。

旧版 Java `PythonChatRequest` 曾偏向 Java 内部上下文结构，字段包括 `threadId`、`userId`、`message`、`profile`、`bodyData`、`preferences`、`chatHistory`、`candidates`。这与 Python 新契约不一致，尤其是 Python 期望 `query`，而不是 `message`。

## 2. 目标 JSON 契约

> **⚠️ 避免混淆：这不是前端请求 Java 的契约**
>
> 这里的 JSON 结构是 **Java 服务端在内部查库并组装好用户画像和商品候选后，作为 HTTP 客户端发给 Python AI 服务**的契约。
> 如果你是在测试前端调用 Java `/api/assistant/chat` 的接口，请**不要**直接拷贝此 JSON 作为请求体。前端请求 Java 只需要极简参数（例如 `{"message": "我想买一件外套"}`），Java 接收后会自动转换。前端请求契约请参考 `AssistantChatRequest.java` 或是 `docs/api-testing-with-reqable.md`。

Java 调用 Python `POST /chat` 时，请求 JSON 应按下面结构发送：

```json
{
  "request_id": "req-xxx",
  "session_id": "sess-xxx",
  "thread_id": "sess-xxx",
  "query": "我想买一件外套",
  "chat_history": [
    {
      "user_query": "上一轮的问题",
      "assistant_answer": "上一轮的回答"
    }
  ],
  "user_context": {
    "user_id": 10001,
    "height_cm": 175.5,
    "weight_kg": 70.0,
    "gender": "male",
    "preferred_fit": "regular",
    "preferred_styles": ["commute", "casual"],
    "preferred_colors": ["black"],
    "disliked_colors": [],
    "preferred_categories": ["外套"],
    "budget_min": null,
    "budget_max": 800.0
  },
  "candidates": [
    {
      "spu_id": 123,
      "sku_id": 456,
      "name": "秋季男士通勤外套",
      "category": "外套",
      "sale_price": 299.0,
      "stock_status": "in_stock",
      "color": "黑色",
      "size": "L",
      "brand": null,
      "material": null,
      "fit_type": "regular",
      "season": ["autumn"],
      "style_tags": ["commute"],
      "main_image_url": null
    }
  ],
  "debug": false
}
```

Python `POST /chat` 成功响应按下面结构返回。Java 会把 `answer`、候选池过滤后的 `product_refs[*].spu_id`，以及 `product_refs[*]` 中的推荐理由和排序分暴露到前端响应：

```json
{
  "request_id": "req-xxx",
  "answer": "推荐优先看通勤外套，版型选择 regular，更适合秋季叠穿。",
  "intent": "recommendation",
  "product_refs": [
    {
      "spu_id": 123,
      "sku_id": 456,
      "reason": "符合通勤、秋季和预算条件",
      "rank_score": 0.95
    }
  ],
  "suggested_actions": [],
  "debug": null
}
```

Java 暴露给前端的 `recommendedSpuIds` 和 `recommendedItems` 不是直接信任 Python 的 `product_refs`。Java 会先确认 `product_refs[*].spu_id` 和 `product_refs[*].sku_id` 同时存在于本轮 Java 传给 Python 的候选池中；候选池外的商品引用会被丢弃。过滤通过后，`reason` 和 `rank_score` 才会进入前端可见的 `recommendedItems[]`。

## 3. 设计原则

- Java 内部 DTO 继续使用驼峰命名，保持 Java 代码可读性。
- Java-Python 边界单独建立请求 DTO，不直接复用前端响应 DTO、数据库模型或 Java 内部上下文对象。
- 所有发给 Python 的 snake_case 字段通过 Jackson `@JsonProperty` 显式声明。
- `AssistantService` 负责把 Java 内部上下文转换成 Python 契约对象。
- `RestPythonAssistantClient` 只负责序列化、HTTP 调用和响应反序列化，不承载业务字段转换。

## 4. 建议 DTO 结构

建议在 `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto` 下维护 Python 专用 DTO。

### 4.1 PythonChatRequest

```java
public record PythonChatRequest(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("thread_id") String threadId,
        @JsonProperty("query") String query,
        @JsonProperty("chat_history") List<PythonChatHistoryItem> chatHistory,
        @JsonProperty("user_context") PythonUserContext userContext,
        @JsonProperty("candidates") List<PythonProductCandidate> candidates,
        @JsonProperty("debug") Boolean debug
) {
}
```

### 4.2 PythonChatHistoryItem

```java
public record PythonChatHistoryItem(
        @JsonProperty("user_query") String userQuery,
        @JsonProperty("assistant_answer") String assistantAnswer
) {
}
```

历史消息不应直接发送 `MessageResponse`。当前 `MessageResponse` 会暴露 `role`、`content`、`status`、`requestId`、`createdAt` 等 Java 内部字段，不符合 Python 的固定历史消息格式。

### 4.3 PythonUserContext

```java
public record PythonUserContext(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("height_cm") BigDecimal heightCm,
        @JsonProperty("weight_kg") BigDecimal weightKg,
        @JsonProperty("gender") String gender,
        @JsonProperty("preferred_fit") String preferredFit,
        @JsonProperty("preferred_styles") List<String> preferredStyles,
        @JsonProperty("preferred_colors") List<String> preferredColors,
        @JsonProperty("disliked_colors") List<String> dislikedColors,
        @JsonProperty("preferred_categories") List<String> preferredCategories,
        @JsonProperty("budget_min") BigDecimal budgetMin,
        @JsonProperty("budget_max") BigDecimal budgetMax
) {
}
```

`user_context` 应由 Java 的 `UserProfileResponse`、`UserBodyDataResponse`、`UserPreferencesResponse` 合并生成，而不是把三个对象嵌套传给 Python。

### 4.4 PythonProductCandidate

```java
public record PythonProductCandidate(
        @JsonProperty("spu_id") Long spuId,
        @JsonProperty("sku_id") Long skuId,
        @JsonProperty("name") String name,
        @JsonProperty("category") String category,
        @JsonProperty("sale_price") BigDecimal salePrice,
        @JsonProperty("stock_status") String stockStatus,
        @JsonProperty("color") String color,
        @JsonProperty("size") String size,
        @JsonProperty("brand") String brand,
        @JsonProperty("material") String material,
        @JsonProperty("fit_type") String fitType,
        @JsonProperty("season") List<String> season,
        @JsonProperty("style_tags") List<String> styleTags,
        @JsonProperty("main_image_url") String mainImageUrl
) {
}
```

候选商品也应使用 Python 专用 DTO，避免把 Java 查询模型里的聚合字段、数据库命名或未来前端展示字段直接泄漏到 Python 契约中。

当前 Java 推荐候选查询仍以 SPU 为主聚合维度，同时会为每个 SPU 聚合出一个代表性 SKU 的 `sku_id`、`sale_price`、`stock_status`、`color` 和 `size`，用于满足 Python `/chat` 对候选商品必填字段的要求。后续如果 Python 需要精确到每个可售 SKU 的多行候选，可再把候选 SQL 从 SPU 聚合升级为 SKU 明细列表。

### 4.5 PythonProductRef

```java
public record PythonProductRef(
        @JsonProperty("spu_id") Long spuId,
        @JsonProperty("sku_id") Long skuId,
        @JsonProperty("reason") String reason,
        @JsonProperty("rank_score") BigDecimal rankScore
) {
}
```

### 4.6 PythonChatResponse

```java
public record PythonChatResponse(
        @JsonProperty("request_id") String requestId,
        @JsonProperty("answer") String answer,
        @JsonProperty("intent") String intent,
        @JsonProperty("product_refs") List<PythonProductRef> productRefs
) {
}
```

## 5. 字段映射规则

`AssistantService.toPythonRequest(...)` 建议执行以下映射：

| Java 来源 | Python 字段 | 说明 |
|---|---|---|
| `MDC.get("requestId")` | `request_id` | 链路追踪 ID。为空时需要决定是否生成兜底值。 |
| `threadId` | `session_id` | 当前阶段可与 Java 会话 threadId 保持一致。 |
| `threadId` | `thread_id` | Python 可选字段，当前阶段同样传 threadId。 |
| `AssistantChatRequest.message()` | `query` | 必须使用 `query`，不能继续发送 `message`。 |
| `AssistantContext.chatHistory()` | `chat_history` | 需要从 user/assistant 消息对转换成 `user_query` 和 `assistant_answer`。 |
| `profile + bodyData + preferences` | `user_context` | 合并为扁平用户画像对象。 |
| `AssistantContext.candidates()` | `candidates` | 转换成 Python 专用候选商品 DTO。 |
| 固定默认值 | `debug` | 第一版默认 `false`。 |
| `PythonChatResponse.productRefs[*].spuId` | `AssistantChatResponse.recommendedSpuIds` | 兼容旧前端和旧测试的 SPU id 列表。 |
| `PythonChatResponse.productRefs[*]` | `AssistantChatResponse.recommendedItems` | 推荐理由展示字段，包含 `spuId`、`skuId`、`reason`、`rankScore`。必须先经过 Java 候选池过滤。 |

## 6. 历史消息转换建议

Python 期望历史消息是问答对，而 Java 数据库中是按消息行保存：

```text
user: 用户问题
assistant: 助手回答
user: 下一轮问题
assistant: 下一轮回答
```

转换时建议按时间顺序扫描：

- 遇到 `role = user` 时暂存为当前 `user_query`。
- 遇到 `role = assistant` 且已有暂存 `user_query` 时，生成一条 `PythonChatHistoryItem`。
- 孤立的 assistant 消息忽略。
- 最后一条没有 assistant 回答的 user 消息不进入 `chat_history`，因为本轮问题已经通过 `query` 传递。

## 7. 测试设计

实现时至少更新两类测试。

### 7.1 RestPythonAssistantClientTests

测试目标：确认实际 HTTP 请求体是 Python 需要的 snake_case JSON。

断言建议：

- 包含 `"request_id"`。
- 包含 `"session_id"`。
- 包含 `"thread_id"`。
- 包含 `"query"`。
- 不包含 `"message"`。
- 包含 `"chat_history"`、`"user_query"`、`"assistant_answer"`。
- 包含 `"user_context"`、`"height_cm"`、`"preferred_styles"`、`"budget_max"`。
- 包含 `"candidates"`、`"spu_id"`、`"sku_id"`、`"sale_price"`、`"stock_status"`、`"season"`。
- 能反序列化 `"product_refs"`，并读取 `"spu_id"`、`"sku_id"`、`"reason"`。

### 7.2 AssistantServiceTests

测试目标：确认业务层传给 Python 客户端的对象已经完成语义映射。

断言建议：

- `requestId` 来自 MDC。
- `sessionId` 等于当前 Java `threadId`。
- `threadId` 等于当前 Java `threadId`。
- `query` 等于前端传入的 `AssistantChatRequest.message()`。
- `debug` 默认为 `false`。
- `userContext.userId` 等于当前登录用户 ID。
- `productRefs[*].spuId` 会映射为前端响应里的 `recommendedSpuIds`。
- `productRefs[*].reason` 和 `productRefs[*].rankScore` 会映射为前端响应里的 `recommendedItems`，且只允许候选池内 `spuId`/`skuId` 通过。

### 7.3 2026-06-19 合同回归测试补充

本轮补充的回归点：

- `AssistantControllerTests` 覆盖 `/api/assistant/chat` 和 `/api/assistant/chat/stream`。测试中 Python 假响应同时返回一个合法候选引用 `1002/2101` 和一个候选池外引用 `9999/8888`，Java 响应只允许出现 `recommendedSpuIds=[1002]` 和候选池内的 `recommendedItems`，不能透出 `9999`。
- `AssistantServiceTests` 覆盖 Python 流式调用失败场景。Java 可以保存用户消息用于排障，但不能把失败流伪装成 assistant 回答写入会话历史。
- `AssistantServiceTests` 覆盖 `recommendedItems` 映射，确认推荐理由和 `skuId` 只从候选池内的 Python `product_refs` 透出。
- `RestPythonAssistantClientTests` 和 `PythonSseEventParserTests` 继续覆盖 snake_case 请求体、`product_refs` 反序列化和 Python SSE 事件解析。

这组测试保护的边界：

- Python 可以参与推荐排序，但最终商品 ID 是否能展示给前端由 Java 候选池决定。
- SSE `done` 事件里的 `recommended_spu_ids` 和 `recommended_items` 必须经过同样的候选池过滤。
- 外部 AI 流失败时返回可诊断错误，不制造假的成功会话。

## 8. 验证命令

实现完成后使用 JDK 21 跑完整验证。Windows PowerShell：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd verify
```

macOS/Linux 下本轮合同回归测试命令：

```bash
cd backend
bash ./mvnw test -Dtest=AssistantControllerTests,AssistantServiceTests,RestPythonAssistantClientTests,PythonSseEventParserTests
```

## 9. 实施边界

本次适配只处理 Java 调用 Python `/chat` 的请求体结构和响应商品引用解析，不改变：

- 前端调用 Java `/api/assistant/chat` 的请求格式。
- Java 内部用户画像、商品、会话模块的数据库模型。
- Python 调用 Java `/internal/**` 的 internal API 契约。
- Python `/chat` 响应结构。

## 10. 待确认点

实现前需要确认一个契约细节：`session_id` 和 `thread_id` 是否长期保持相同。

当前建议：第一版二者都使用 Java `threadId`。如果 Python 后续需要独立 session 概念，再增加显式映射字段。
