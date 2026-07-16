# LLM Demand Intent Patch MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有 Java 确定性需求解析与商品筛选闭环中加入受控的 LLM 语义补丁和结构化待确认状态，让模糊购物表达能够被补全或追问，同时保证未经 Java 验证的内容永远不会进入 SQL。

**Architecture:** Java 先产出确定性补丁、锁定槽位和未解析文本，仅在触发条件成立时调用 Python 内部解析接口。Python 只返回严格、稀疏的候选 JSON；Java 再执行枚举、证据、置信度、锁定槽位和预算校验，决定合并正式 `DemandIntent` 或持久化 `PendingClarification`。待确认状态与正式筛选快照分离，并由 Java 在确认、取消或新请求时完成状态转换。

**Tech Stack:** Java 21, Spring Boot, MyBatis, Flyway, JUnit 5, Mockito, Python 3.12, FastAPI, Pydantic v2, LangChain/OpenAI-compatible chat model, pytest.

---

## 文件结构

### Python 项目：`AI Clothing Shopping Assistant System`

- `clothing_assistant/domain/demand_intent_models.py`：严格枚举、证据和 LLM 候选响应模型。
- `clothing_assistant/application/demand_intent_parse_service.py`：提示词、模型调用、JSON 解码与 Pydantic 校验。
- `clothing_assistant/api/schemas.py`：内部接口请求/响应 DTO。
- `clothing_assistant/api/app.py`：受内部令牌保护的 `/internal/demand-intent/parse` 路由。
- `clothing_assistant/infrastructure/llm_client.py`：无重试的单次结构化模型调用。
- `tests/test_demand_intent_parse_service.py`、`tests/test_demand_intent_parse_api.py`：服务和 HTTP 契约测试。

### Java 项目：`Intelligent Outfit Recommendation System/backend`

- `assistant/dto/DeterministicDemandParseResult.java`：确定性解析补丁、锁定槽位、匹配片段、未解析文本和购物信号。
- `assistant/dto/LlmDemandParseRequest.java`、`LlmDemandParseResponse.java`、`SlotEvidence.java`、`PendingClarification.java`：跨服务契约和待确认结构。
- `assistant/service/DemandIntentResolver.java`：返回带元数据的确定性解析结果。
- `assistant/service/DemandIntentParseTrigger.java`：隔离是否调用 LLM 的纯规则。
- `assistant/service/LlmDemandIntentValidator.java`：Java 最终安全边界。
- `assistant/service/DemandIntentNormalizer.java`：将大写跨服务枚举映射为现有领域值。
- `assistant/client/DemandIntentParseClient.java`、`RestDemandIntentParseClient.java`、`ResilientDemandIntentParseClient.java`：8 秒超时、内部鉴权和失败降级。
- `conversation/dto/ConversationDemandStateSnapshot.java`、`conversation/service/ConversationDemandStateStore.java`：向 assistant 暴露不泄漏 mapper/model 的状态边界。
- `conversation/model/ChatDemandState.java`、`ChatDemandTransition.java`、`conversation/mapper/ConversationMapper.java` 及 XML：待确认状态和转换审计。
- `db/migration/V21__pending_demand_clarification.sql`：状态与转换表增加待确认 JSON。
- `assistant/service/DemandIntentStateService.java`、`AssistantContextService.java`、`AssistantService.java`：解析、验证、持久化、追问和候选筛选编排。

---

### Task 1: Python 严格解析领域模型

**Files:**
- Create: `AI Clothing Shopping Assistant System/clothing_assistant/domain/demand_intent_models.py`
- Test: `AI Clothing Shopping Assistant System/tests/test_demand_intent_parse_service.py`

- [ ] **Step 1: 写失败测试**

测试 `MERGE` 只接受已声明槽位和对象证据，拒绝额外字段、字符串证据、非法 action，以及 `needsClarification` 与澄清字段不一致的响应。

```python
def test_candidate_rejects_string_evidence():
    with pytest.raises(ValidationError):
        DemandIntentParseCandidate.model_validate({
            "schemaVersion": "1.0", "action": "MERGE",
            "slots": {"targetGender": "FEMALE"},
            "slotConfidence": {"targetGender": 0.93},
            "evidence": {"targetGender": ["女朋友"]},
            "needsClarification": False,
        })
```

- [ ] **Step 2: 验证 RED**

Run: `python -m pytest tests/test_demand_intent_parse_service.py -q`
Expected: FAIL，因为模型模块不存在。

- [ ] **Step 3: 最小实现**

使用 `ConfigDict(extra="forbid")` 建立 `ParseAction(MERGE, CLARIFY)`、`EvidenceSource(CURRENT_MESSAGE, PENDING_CLARIFICATION)`、`SlotEvidence`、稀疏 `DemandIntentSlots`、置信度模型和 `DemandIntentParseCandidate`；模型级校验保证：slots 中每个字段都有 confidence/evidence，未出现槽位不携带元数据，CLARIFY 恰好携带一个槽位和问题。

- [ ] **Step 4: 验证 GREEN**

Run: `python -m pytest tests/test_demand_intent_parse_service.py -q`
Expected: PASS。

- [ ] **Step 5: 提交**

```powershell
git commit --only clothing_assistant/domain/demand_intent_models.py tests/test_demand_intent_parse_service.py -m "feat: define strict demand intent parse contract"
```

### Task 2: Python 解析服务与内部 API

**Files:**
- Create: `AI Clothing Shopping Assistant System/clothing_assistant/application/demand_intent_parse_service.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/infrastructure/llm_client.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/api/schemas.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/api/app.py`
- Test: `AI Clothing Shopping Assistant System/tests/test_demand_intent_parse_service.py`
- Test: `AI Clothing Shopping Assistant System/tests/test_demand_intent_parse_api.py`

- [ ] **Step 1: 写服务失败测试**

注入返回 JSON 字符串的模型函数，验证服务只接受 `json.loads` 后通过严格 Pydantic 校验的对象；Markdown 代码块、额外字段和非 JSON 均抛出受控 `DemandIntentParseError`。验证提示词明确普通历史不得成为 evidence。

- [ ] **Step 2: 验证服务 RED**

Run: `python -m pytest tests/test_demand_intent_parse_service.py -q`
Expected: FAIL，因为解析服务不存在。

- [ ] **Step 3: 实现无重试单次调用**

在 `llm_client.py` 增加异步 `invoke_chat_content(messages, timeout_seconds=8)`，使用 `asyncio.timeout(8)` 包裹一次 `ainvoke`，不添加应用层重试；在解析服务中构造系统提示和结构化上下文，执行严格 JSON 解码和 Pydantic 校验。

- [ ] **Step 4: 写 API 失败测试**

覆盖：缺少/错误内部令牌返回 401；合法请求返回严格 JSON；服务异常返回 503 且不泄漏模型错误；请求包含最多三轮历史、lockedSlots 和 pendingClarification。

- [ ] **Step 5: 验证 API RED**

Run: `python -m pytest tests/test_demand_intent_parse_api.py -q`
Expected: FAIL，路由返回 404。

- [ ] **Step 6: 实现 API**

在 `schemas.py` 定义 snake_case 请求模型并使用 aliases 对齐 Java camelCase；在 `app.py` 注册 `POST /internal/demand-intent/parse`，复用 `Depends(require_internal_auth)`，调用解析服务并将受控失败转换为 503。

- [ ] **Step 7: 验证 Python 子系统并提交**

Run: `python -m pytest tests/test_demand_intent_parse_service.py tests/test_demand_intent_parse_api.py -q`
Expected: PASS。

```powershell
git commit --only clothing_assistant/application/demand_intent_parse_service.py clothing_assistant/infrastructure/llm_client.py clothing_assistant/api/schemas.py clothing_assistant/api/app.py tests/test_demand_intent_parse_service.py tests/test_demand_intent_parse_api.py -m "feat: add internal demand intent parser"
```

### Task 3: Java 确定性解析元数据与触发规则

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DeterministicDemandParseResult.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentParseTrigger.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentResolverTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentParseTriggerTests.java`

- [ ] **Step 1: 写失败测试**

覆盖“男性穿搭”锁定 `targetGender` 并保留购物信号、“给女朋友找成熟硬朗外套”解析确定性性别/分类并把风格语义留在 unresolvedText、普通连接词不触发、政策/订单/库存问题不触发、存在 pending 时触发。

- [ ] **Step 2: 验证 RED**

Run: `./mvnw -Dtest=DemandIntentResolverTests,DemandIntentParseTriggerTests test`
Expected: FAIL，因为结果 DTO 和触发器不存在。

- [ ] **Step 3: 最小实现并保持旧入口兼容**

新增 `resolveDetailed(request)`，原 `resolvePatch(request)` 委托给它返回 `deterministicPatch`。触发器仅在 pending、部分解析后仍有有效文本、或无槽位但含购物信号时返回 true，并排除非购物意图。

- [ ] **Step 4: 验证 GREEN 并提交**

Run: `./mvnw -Dtest=DemandIntentResolverTests,DemandIntentParseTriggerTests test`
Expected: PASS。

```powershell
git commit --only backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DeterministicDemandParseResult.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentParseTrigger.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentResolverTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentParseTriggerTests.java -m "feat: expose deterministic demand parse metadata"
```

### Task 4: Java Python 解析客户端

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/LlmDemandParseRequest.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/LlmDemandParseResponse.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/SlotEvidence.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/DemandIntentParseClient.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestDemandIntentParseClient.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientDemandIntentParseClient.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestDemandIntentParseClientTests.java`

- [ ] **Step 1: 写失败契约测试**

本地 HTTP stub 验证 URL、`X-Internal-Token`、camelCase JSON、8 秒 request timeout 和响应反序列化；验证超时、5xx、非法 JSON 均由 resilient wrapper 返回 empty 而不是中断导购。

- [ ] **Step 2: 验证 RED**

Run: `./mvnw -Dtest=RestDemandIntentParseClientTests test`
Expected: FAIL，因为客户端不存在。

- [ ] **Step 3: 按现有 RestPythonAssistantClient 模式实现**

使用 Spring 管理的 `HttpClient`/`ObjectMapper`、配置的 Python base URL 和内部令牌，请求路径固定为 `/internal/demand-intent/parse`。接口返回 `Optional<LlmDemandParseResponse>`，降级层捕获传输和协议错误并记录 requestId。

- [ ] **Step 4: 验证 GREEN 并提交**

Run: `./mvnw -Dtest=RestDemandIntentParseClientTests test`
Expected: PASS。

```powershell
git commit --only backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/LlmDemandParseRequest.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/LlmDemandParseResponse.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/SlotEvidence.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/DemandIntentParseClient.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestDemandIntentParseClient.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientDemandIntentParseClient.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestDemandIntentParseClientTests.java -m "feat: call demand intent parser safely"
```

### Task 5: Java 候选校验和枚举归一化

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/LlmDemandIntentValidator.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentNormalizer.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ValidatedDemandParseResult.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/LlmDemandIntentValidatorTests.java`

- [ ] **Step 1: 写失败测试**

逐项覆盖：locked slot 被拒绝；CURRENT_MESSAGE 必须精确子串；PENDING 证据必须匹配 pending 原文和 slot；普通历史来源被拒绝；性别 `.85`、分类 `.80`、预算 `.95`、软条件 `.65` 阈值；预算必须有精确数字证据并在允许范围；未知枚举被拒绝；低置信度软条件静默丢弃；低置信度硬条件生成单一 pending。

- [ ] **Step 2: 验证 RED**

Run: `./mvnw -Dtest=LlmDemandIntentValidatorTests test`
Expected: FAIL，因为 validator 不存在。

- [ ] **Step 3: 最小实现**

validator 返回 `ValidatedDemandParseResult`，只包含正式 patch 或一个 `PendingClarification`。normalizer 使用显式不可变映射将 `MALE/FEMALE/UNISEX`、`OUTERWEAR` 等映射到现有 Java/SQL 值，不对未知值做猜测。

- [ ] **Step 4: 验证 GREEN 并提交**

Run: `./mvnw -Dtest=LlmDemandIntentValidatorTests test`
Expected: PASS。

```powershell
git commit --only backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/LlmDemandIntentValidator.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentNormalizer.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ValidatedDemandParseResult.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/LlmDemandIntentValidatorTests.java -m "feat: validate LLM demand patches in Java"
```

### Task 6: PendingClarification 持久化和状态转换

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__pending_demand_clarification.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PendingClarification.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/ConversationDemandStateSnapshot.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatDemandState.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatDemandTransition.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/mapper/ConversationMapper.java`
- Modify: `backend/src/main/resources/mapper/ConversationMapper.xml`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationDemandStateStore.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentStateService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationDemandStateStoreTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentStateServiceTests.java`

- [ ] **Step 1: 写失败状态测试**

覆盖 `clarify` 只写 pending 不改 effective intent；`confirm` 原子合并补丁并清 pending；`cancel_clarify` 只清 pending；新 clarification 替换旧值；相同 requestId 重放得到相同结果；乐观锁冲突只重试一次。

- [ ] **Step 2: 验证 RED**

Run: `./mvnw -Dtest=ConversationDemandStateStoreTests,DemandIntentStateServiceTests test`
Expected: FAIL，因为状态尚无 pending 字段。

- [ ] **Step 3: 实现 V21 和模块边界**

V21 给 state 和 transition 都增加 `pending_clarification_json`，确保幂等重放能恢复当次结果。conversation 模块只暴露 JSON 快照 DTO；assistant 模块负责序列化领域对象，继续禁止直接访问 conversation mapper/model。

- [ ] **Step 4: 验证 GREEN、架构测试并提交**

Run: `./mvnw -Dtest=ConversationDemandStateStoreTests,DemandIntentStateServiceTests,ModuleArchitectureTests test`
Expected: PASS。

```powershell
git commit --only backend/src/main/resources/db/migration/V21__pending_demand_clarification.sql backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PendingClarification.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/ConversationDemandStateSnapshot.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatDemandState.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatDemandTransition.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/mapper/ConversationMapper.java backend/src/main/resources/mapper/ConversationMapper.xml backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationDemandStateStore.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentStateService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationDemandStateStoreTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentStateServiceTests.java -m "feat: persist pending demand clarification"
```

### Task 7: 对话编排、追问和失败降级

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantContext.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

- [ ] **Step 1: 写失败编排测试**

覆盖：完整确定性输入不调用 LLM；部分解析调用并只合并通过验证的补丁；CLARIFY 持久化 pending、保持旧快照、直接向用户返回单一问题且不调用普通 Python chat；确认回答将 pending 候选转为正式补丁；“算了”清 pending；显式新需求和政策/订单/库存问题取消旧 pending；解析服务失败时保留确定性补丁；完全模糊且失败时返回通用追问而不是技术错误。

- [ ] **Step 2: 验证 RED**

Run: `./mvnw -Dtest=AssistantContextServiceTests,AssistantServiceTests test`
Expected: FAIL，因为上下文还没有 clarification 输出和新编排。

- [ ] **Step 3: 实现编排**

`AssistantContextService` 顺序固定为：读取状态与最多三轮/4000 字历史 → 确定性解析 → pending 生命周期判断 → 条件调用 parser → Java 校验 → 原子状态转换 → 使用 effective intent 构造 SQL 候选。`AssistantContext` 增加可空 `clarificationQuestion`；`AssistantService` 在它非空时直接生成同步/流式追问响应，避免让普通聊天模型改写问题。

- [ ] **Step 4: 验证 GREEN 并提交**

Run: `./mvnw -Dtest=AssistantContextServiceTests,AssistantServiceTests test`
Expected: PASS。

```powershell
git commit --only backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantContext.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java -m "feat: orchestrate LLM demand clarification"
```

### Task 8: 契约、回归和开发文档收口

**Files:**
- Modify: `Intelligent Outfit Recommendation System/docs/superpowers/specs/2026-07-16-llm-demand-intent-patch-mvp-design.md`
- Modify: `Intelligent Outfit Recommendation System/README.md`（仅在现有开发进度表存在对应入口时）
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java`

- [ ] **Step 1: 增加共享契约失败测试**

读取 Python schema/示例夹具，验证 Java/Python 对 `schemaVersion`、action、slots、evidence source 和字段命名一致。

- [ ] **Step 2: 验证 RED 并修正契约**

Run: `./mvnw -Dtest=SharedJavaPythonContractTests test`
Expected: 首次因缺少新契约断言而 FAIL；统一两端字段后 PASS。

- [ ] **Step 3: Python 回归**

Run: `python -m pytest -q`
Expected: 全部 PASS；若本机 `.env` 的内部令牌改变历史 API 测试环境，临时移出 `.env` 后重跑并恢复文件。

Run: `python -m ruff check clothing_assistant tests`
Expected: PASS。

- [ ] **Step 4: Java 回归**

Run: `./mvnw test`
Expected: 非 Docker 测试全部 PASS；若 Testcontainers 因 Docker Desktop 网络/引擎不可用失败，单独记录基础设施失败并运行排除 Docker 的测试集。

Run: `./mvnw checkstyle:check`
Expected: PASS，0 violations。

- [ ] **Step 5: 文档状态更新与精确提交**

将设计文档状态改为“已实施”，补充实际类图/调用链、配置项、测试证据和已知环境限制；只提交本功能文件，不包含其他任务的 frontend、IDE、learning_demos 或未跟踪文档。

```powershell
git commit --only docs/superpowers/specs/2026-07-16-llm-demand-intent-patch-mvp-design.md backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java -m "docs: complete demand intent patch MVP"
```

---

## 完成判定

- “男性”“女性呢”“给女朋友找成熟硬朗外套”等表达不会因为规则词典不完整而直接产生错误 SQL 条件。
- 所有进入 `DemandIntent` 的 LLM 槽位都通过 Java 的枚举、证据、置信度和 lockedSlots 校验。
- `PendingClarification` 不进入 SQL，且确认、取消、替换、幂等重放都有持久化测试。
- Python 不可用时，确定性筛选仍可用；完全模糊时返回业务追问而不是 `python_stream_unavailable`。
- 普通历史只能帮助理解上下文，不能成为硬条件 evidence。
- 计划列出的 Python/Java 定向测试、架构测试、静态检查和可运行的回归测试全部通过。
