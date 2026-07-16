# LLM Demand Intent Patch MVP Remediation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 MVP 审查发现的跨服务契约、Java 安全边界、Pending 生命周期、并发幂等与测试缺口，使实现真正满足 `2026-07-16-llm-demand-intent-patch-mvp-design.md`。

**Architecture:** 保持“Python 提候选、Java 最终裁决”的既有边界，不推翻现有模块。先统一 Java/Python 契约，再把 Pending 状态转换建模为显式 action，最后收紧候选查询、熔断和持久化并发行为；所有修复均以失败测试先行。

**Tech Stack:** Java 21, Spring Boot, MyBatis, Flyway, Resilience4j, JUnit 5, Python 3.12, FastAPI, Pydantic v2, pytest.

---

## 1. 审查结论

当前状态为“核心框架完成，MVP 修复中”，不能视为最终验收完成。自动化测试虽然通过，但尚未覆盖文档中的完整契约和状态机。

| 编号 | 缺口 | 根因 | 验收结果 |
| --- | --- | --- | --- |
| R1 | CLARIFY 与文档相反 | Python 把 CLARIFY 候选错误塞入 `slots`，且缺少 `clarificationCandidateValue` | 文档中的 CLARIFY 示例可被两端正确解析 |
| R2 | 枚举和空数组不严格 | Python 使用自由字符串；Java 对 soft slots 仅转小写 | 非法枚举、空数组和多余字段在边界被拒绝 |
| R3 | Java 最终裁决不完整 | 未完整校验 action、字段关系和 CLARIFY 安全条件 | 未知 action、冲突 locked slot、非法证据不能生成 patch/pending |
| R4 | Pending evidence 候选不一致 | 只校验 slot/rawText，没有校验 candidateValue | MALE pending 不能被 FEMALE 证据确认 |
| R5 | confirm/cancel 审计错误 | 状态服务只根据是否存在新 pending 选择 merge/clarify | transition 正确写入 `confirm` / `cancel_clarify` |
| R6 | 取消仍改变快照 | 空补丁仍经 merger 更新 rawQuery | “算了”只清 pending，有效快照逐字段完全不变 |
| R7 | 请求上下文不完整 | Java 请求缺 `schemaVersion/currentDemand` | Python 收到当前有效快照和版本 |
| R8 | 历史上限错误 | 每条截 2000，而非总计 4000 | 最近三轮完整问答总字符不超过 4000，从最旧内容裁剪 |
| R9 | 非购物中断不完整 | 漏掉价格和普通库存关键词 | pending 遇到价格/库存/订单/售后直接取消且不调解析器 |
| R10 | 软条件进入 SQL | candidate query 直接消费 `DemandIntent.style` | LLM scene/style/attributes 只交给 Python 排序，不做 SQL 硬过滤 |
| R11 | 并发幂等不完整 | 乐观锁失败后无请求顺序保护地重放旧 mutation | 同 requestId 并发只落一次；旧请求不能覆盖新状态 |
| R12 | 独立熔断缺失 | resilient client 只有 try/catch | 解析客户端使用独立 Resilience4j circuit breaker |
| R13 | Python 依赖错误为 500 | service 未归一 `DependencyError` | 模型缺密钥、超时、连接错误统一返回安全 503 |
| R14 | 测试与文档未收口 | 实施时只覆盖主路径 | 生命周期、契约、并发、降级和三条 E2E 全部有测试 |

## 2. 修复顺序

### Task 1：统一 Python 严格契约

**Files:**
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/domain/demand_intent_models.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/api/schemas.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/application/demand_intent_parse_service.py`
- Test: `AI Clothing Shopping Assistant System/tests/test_demand_intent_parse_service.py`
- Test: `AI Clothing Shopping Assistant System/tests/test_demand_intent_parse_api.py`

- [ ] RED：合法空 slots 的 CLARIFY 当前失败；非法枚举、空数组当前通过；DependencyError 当前返回 500。
- [ ] GREEN：增加受限枚举、`clarificationCandidateValue` 和 MERGE/CLARIFY 分支校验。
- [ ] GREEN：把上游依赖错误归一为 `DemandIntentParseError`，API 返回 503。
- [ ] VERIFY：定向 pytest 与 Ruff 通过。

### Task 2：收紧 Java 最终安全边界

**Files:**
- Modify: `backend/src/main/java/.../assistant/dto/LlmDemandParseRequest.java`
- Modify: `backend/src/main/java/.../assistant/dto/LlmDemandParseResponse.java`
- Modify: `backend/src/main/java/.../assistant/client/RestDemandIntentParseClient.java`
- Modify: `backend/src/main/java/.../assistant/service/LlmDemandIntentValidator.java`
- Modify: `backend/src/main/java/.../assistant/service/DemandIntentNormalizer.java`
- Test: `backend/src/test/java/.../assistant/LlmDemandIntentValidatorTests.java`
- Test: `backend/src/test/java/.../assistant/SharedJavaPythonContractTests.java`

- [ ] RED：未知 action、非法 CLARIFY、soft 非法枚举、pending 候选值冲突当前未被拒绝。
- [ ] GREEN：按设计顺序执行 schema/action/字段关系/枚举/evidence/lock/confidence/range 校验。
- [ ] GREEN：Java 请求加入 `schemaVersion/currentDemand`，响应加入 `clarificationCandidateValue`。
- [ ] VERIFY：Java/Python 共享契约测试通过。

### Task 3：修复 Pending 生命周期和历史上下文

**Files:**
- Modify: `backend/src/main/java/.../assistant/dto/PendingClarification.java`
- Modify: `backend/src/main/java/.../assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/.../assistant/service/DemandIntentStateService.java`
- Test: `backend/src/test/java/.../assistant/AssistantContextServiceTests.java`
- Test: `backend/src/test/java/.../assistant/DemandIntentStateServiceTests.java`

- [ ] RED：确认/取消审计 action、取消快照不变、价格/库存中断、三轮 4000 字限制测试失败。
- [ ] GREEN：状态服务接收显式 transition action；取消使用 identity mutation。
- [ ] GREEN：pending 增加 `sourceRequestId`，确认值由统一 normalizer 转为正式 patch。
- [ ] GREEN：历史按总长度 4000 从最旧内容裁剪，并发送 currentDemand。

### Task 4：修复 SQL 软硬边界、熔断和并发幂等

**Files:**
- Modify: `backend/src/main/java/.../assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/.../assistant/client/ResilientDemandIntentParseClient.java`
- Modify: `backend/src/main/java/.../conversation/service/ConversationDemandStateStore.java`
- Test: `backend/src/test/java/.../assistant/AssistantContextServiceTests.java`
- Test: `backend/src/test/java/.../assistant/AssistantResilienceConfigTests.java`
- Create: `backend/src/test/java/.../conversation/ConversationDemandStateStoreTests.java`

- [ ] RED：LLM style 进入 candidate SQL、熔断未开启、并发旧请求覆盖新状态。
- [ ] GREEN：只有显式前端 style 控件可作为旧 SQL style 参数；语义 style 保留为软偏好。
- [ ] GREEN：解析客户端使用独立 circuit breaker 配置和指标名称。
- [ ] GREEN：重复 requestId 在竞争后重新读取 transition；并发版本冲突不盲目重放旧请求。

### Task 5：回归、E2E 与文档验收

- [ ] Python 全量 pytest、Ruff 通过。
- [ ] Java 非 Docker 全量测试、Checkstyle、模块架构测试通过。
- [ ] Docker 可用时运行 RabbitMQ/Testcontainers；不可用时只记录基础设施限制。
- [ ] 增加三条设计文档 E2E 场景测试。
- [ ] 将原设计状态更新为“已修复并验收”，逐项勾选本文件。

## 3. 明确不在本次修复范围

- 颜色槽位进入 SQL；
- 全商品 male/female/unisex 人工校准；
- 历史快照恢复和男女款对比专用流程。

上述三项继续保留在后续开发清单，不得借本次修复扩大范围。
