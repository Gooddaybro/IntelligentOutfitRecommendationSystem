# 自然语言穿搭请求修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 严格落实已确认设计，使复合穿搭输入能够被正确解析、筛选、回答和展示，并修复资料表单可读性与身体数据安全保存问题。

**Architecture:** Java 维护 `demand-intent-v2`、商品候选与最终推荐状态，Python 只在 Java 候选池内返回排序引用和结构化匹配证据，前端通过 `recommendationId` 读取同一候选快照。咨询对象测量数据保持会话作用域，只有明确属于本人且经用户确认时，才通过窄字段 PATCH 写入个人资料。

**Tech Stack:** Java 21、Spring Boot、MyBatis、Flyway、MySQL、Python 3.13、FastAPI、Pydantic、React 18、TypeScript、Vitest、Playwright。

---

## 文件结构

### 共享契约

- Modify: `../outfit-project-contract/contracts/java-python-chat/v1.fields.json`

### Java 后端

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DemandIntent.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DemandIntentPatch.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/SubjectMeasurements.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/MatchedDimension.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/RecommendationDecisionService.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/OutfitRoleResolver.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentMerger.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantRecommendationItem.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/api/AssistantController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/service/RecommendationAttributionService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/mapper/RecommendationAttributionMapper.java`
- Modify: `backend/src/main/resources/mapper/behavior/RecommendationAttributionMapper.xml`
- Create: `backend/src/main/resources/db/migration/V23__recommendation_candidate_position.sql`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/api/UserProfileController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/service/UserProfileService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/mapper/UserProfileMapper.java`
- Modify: `backend/src/main/resources/mapper/user/UserProfileMapper.xml`

### Python AI

- Modify: `../AI Clothing Shopping Assistant System/clothing_assistant/api/schemas.py`
- Modify: `../AI Clothing Shopping Assistant System/clothing_assistant/agent/router.py`
- Modify: `../AI Clothing Shopping Assistant System/clothing_assistant/application/recommendation_service.py`
- Modify: `../AI Clothing Shopping Assistant System/clothing_assistant/application/answer_service.py`
- Modify: `../AI Clothing Shopping Assistant System/clothing_assistant/agent/nodes.py`

### 前端

- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/assistantStream.ts`
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/features/assistant/assistantState.ts`
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Modify: `frontend/src/pages/ProfilePreferencesPage.tsx`
- Modify: `frontend/src/styles.css`

---

### Task 1：升级统一需求契约

**Files:**
- Modify: `backend/.../assistant/dto/DemandIntent.java`
- Modify: `backend/.../assistant/dto/DemandIntentPatch.java`
- Create: `backend/.../assistant/dto/SubjectMeasurements.java`
- Modify: `backend/.../assistant/service/DemandIntentResolver.java`
- Modify: `backend/.../assistant/service/DemandIntentMerger.java`
- Test: `backend/src/test/java/.../assistant/DemandIntentResolverTests.java`
- Test: `backend/src/test/java/.../assistant/DemandIntentMergerTests.java`

- [x] **Step 1: 写失败测试**

覆盖：混合问候进入 `OUTFIT_ADVICE`、`177 130` 归一化、`SELF/OTHER/UNKNOWN`、季节进入硬过滤、条件补充保留 capabilities、切换对象清理测量。

```java
assertThat(intent.version()).isEqualTo("demand-intent-v2");
assertThat(intent.requestType()).isEqualTo("OUTFIT_ADVICE");
assertThat(intent.requestedCapabilities()).contains("OUTFIT_PLAN", "PRODUCT_SELECTION");
assertThat(intent.subjectMeasurements().weightKg()).isEqualByComparingTo("65");
assertThat(intent.subjectMeasurements().normalizedFrom()).isEqualTo("ASSUMED_JIN");
```

- [x] **Step 2: 运行测试确认失败**

Run: `backend\mvnw.cmd -f backend\pom.xml -Dtest=DemandIntentResolverTests,DemandIntentMergerTests test`

- [x] **Step 3: 实现 demand-intent-v2**

保持旧 JSON 可读；规范字段只存在于 `demand_intent`；请求类型与附加能力使用稳定枚举字符串。

- [x] **Step 4: 运行测试确认通过**

- [x] **Step 5: 中文提交**

```text
后端：升级穿搭需求意图契约
```

### Task 2：实现可验证强匹配与角色映射

**Files:**
- Create: `backend/.../assistant/dto/MatchedDimension.java`
- Create: `backend/.../assistant/service/RecommendationDecisionService.java`
- Create: `backend/.../assistant/service/OutfitRoleResolver.java`
- Modify: `backend/.../assistant/dto/AssistantRecommendationItem.java`
- Modify: `backend/.../assistant/service/AssistantService.java`
- Test: `backend/src/test/java/.../assistant/RecommendationDecisionServiceTests.java`
- Test: `backend/src/test/java/.../assistant/OutfitRoleResolverTests.java`

- [x] **Step 1: 写失败测试**

验证只有库存分的引用降级为 `WEAK_FALLBACK`，季节/风格证据经 Java 候选事实复核后才成为 `STRONG_MATCH`，非法证据被拒绝，分类角色由 Java 生成。

- [x] **Step 2: 运行测试确认失败**

- [x] **Step 3: 实现深模块接口**

```java
RecommendationDecision decide(DemandIntent intent,
                              List<RecommendationCandidate> candidates,
                              List<PythonProductRef> refs);
```

模块内部完成商品身份、证据、状态和角色处理；同步与流式调用同一接口。

- [x] **Step 4: 运行测试确认通过**

- [x] **Step 5: 中文提交**

```text
后端：统一强匹配判定与穿搭角色
```

### Task 3：持久化并读取同一候选快照

**Files:**
- Create: `backend/src/main/resources/db/migration/V23__recommendation_candidate_position.sql`
- Modify: `backend/.../behavior/service/RecommendationAttributionService.java`
- Modify: `backend/.../behavior/mapper/RecommendationAttributionMapper.java`
- Modify: `backend/src/main/resources/mapper/behavior/RecommendationAttributionMapper.xml`
- Modify: `backend/.../assistant/api/AssistantController.java`
- Test: `backend/src/test/java/.../behavior/RecommendationAttributionServiceTests.java`
- Test: `backend/src/test/java/.../assistant/AssistantControllerTests.java`

- [ ] **Step 1: 写失败测试**

验证 `candidate_position`、用户所有权、已选排序优先、弱候选仍可读取、其他用户返回 404/403、实时下架商品不可购买。

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现接口**

```http
GET /api/assistant/recommendations/{recommendationId}/candidates
```

接口只按快照 SKU 恢复商品，不重新构造筛选条件。

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: 中文提交**

```text
后端：提供推荐候选快照读取接口
```

### Task 4：实现身体数据安全保存

**Files:**
- Modify: `backend/.../user/api/UserProfileController.java`
- Modify: `backend/.../user/service/UserProfileService.java`
- Modify: `backend/.../user/mapper/UserProfileMapper.java`
- Modify: `backend/src/main/resources/mapper/user/UserProfileMapper.xml`
- Test: `backend/src/test/java/.../user/UserProfileServiceTests.java`

- [ ] **Step 1: 写失败测试**

验证 PATCH 只修改身高体重，保留性别、肩宽、胸围、腰围、臀围和版型；重复提交幂等；非法范围返回 400。

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现窄字段接口**

```http
PATCH /api/me/body-data/measurements
Content-Type: application/json

{"heightCm":177,"weightKg":65}
```

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: 中文提交**

```text
后端：支持安全保存身高体重
```

### Task 5：修复 Python 主任务路由与结构化证据

**Files:**
- Modify: `clothing_assistant/api/schemas.py`
- Modify: `clothing_assistant/agent/router.py`
- Modify: `clothing_assistant/application/recommendation_service.py`
- Test: `tests/test_agent_pipeline.py`
- Test: `tests/test_recommendation_service.py`
- Test: `tests/test_api.py`

- [ ] **Step 1: 写失败测试**

覆盖纯问候、问候加穿搭、穿搭加尺码、裸数字只作为辅助信号、v1 兼容路由、无显式匹配证据时 `product_refs=[]`。

- [ ] **Step 2: 运行测试确认失败**

Run: `.venv\Scripts\python.exe -m pytest tests/test_agent_pipeline.py tests/test_recommendation_service.py tests/test_api.py -q`

- [ ] **Step 3: 实现兼容路由和 `matched_dimensions`**

排序分继续保留调试用途，但 `product_refs` 只输出具有结构化显式需求证据的候选。

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: 中文提交**

```text
AI：修复复合穿搭意图与匹配证据
```

### Task 6：实现穿搭回答编排

**Files:**
- Modify: `clothing_assistant/application/answer_service.py`
- Modify: `clothing_assistant/agent/nodes.py`
- Test: `tests/test_agent_pipeline.py`
- Test: `tests/test_answer_service.py`

- [ ] **Step 1: 写失败测试**

验证 `OUTFIT_ADVICE` 按需求确认、搭配公式、版型材质颜色、真实商品、可选追问输出；无强匹配仍给穿搭方案，不虚构商品。

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现回答模板与降级回答**

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: 中文提交**

```text
AI：增加整套穿搭回答编排
```

### Task 7：前端消费最终状态与候选快照

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/client.ts`
- Modify: `frontend/src/shared/api/assistantStream.ts`
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/features/assistant/assistantState.ts`
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Modify: `frontend/src/features/catalog/ProductCard.tsx`
- Test: `frontend/src/features/assistant/assistantState.test.ts`
- Test: `frontend/src/pages/AiShoppingPage.test.tsx`
- Test: `frontend/src/features/catalog/ProductCard.test.tsx`

- [ ] **Step 1: 写失败测试**

验证状态机、迟到 requestId、快照接口、强/弱/空/错误展示、弱候选无归因、rankScore 不显示百分比、卡片版式不生成 AI 标签。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- --run src/features/assistant/assistantState.test.ts src/pages/AiShoppingPage.test.tsx src/features/catalog/ProductCard.test.tsx`

- [ ] **Step 3: 实现前端状态机和快照消费**

对话完成后按 `recommendationId` 获取候选；不再从 `resolvedIntent` 重建查询。

- [ ] **Step 4: 运行测试确认通过**

- [ ] **Step 5: 中文提交**

```text
前端：统一推荐状态与候选快照展示
```

### Task 8：实现分组穿搭、资料表单和一键保存

**Files:**
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Modify: `frontend/src/pages/ProfilePreferencesPage.tsx`
- Modify: `frontend/src/styles.css`
- Test: `frontend/src/pages/ProfilePreferencesPage.test.tsx`
- Test: `frontend/src/pages/AiShoppingPage.test.tsx`

- [ ] **Step 1: 写失败测试**

验证角色分组、缺失角色不造商品、`SELF` 保存入口、`OTHER/UNKNOWN` 隐藏、换算提示、PATCH 保存、表单深色控件和可访问标签。

- [ ] **Step 2: 运行测试确认失败**

- [ ] **Step 3: 实现方案 A、方案 B 和方案 C**

保持原生 select/date，使用 `color-scheme: dark`，只调用身高体重 PATCH。

- [ ] **Step 4: 运行测试确认通过并构建**

Run: `npm test -- --run`

Run: `npm run build`

- [ ] **Step 5: 中文提交**

```text
前端：完善穿搭分组与资料输入体验
```

### Task 9：全链路验证

**Files:**
- Modify: related regression tests only when a verified defect requires it

- [ ] **Step 1: Java 全量验证**

Run: `backend\mvnw.cmd -f backend\pom.xml verify`

- [ ] **Step 2: Python 全量验证**

Run: `.venv\Scripts\python.exe -m pytest -q`

- [ ] **Step 3: 前端全量测试与构建**

Run: `npm test -- --run`

Run: `npm run build`

- [ ] **Step 4: 主验收用例**

输入：`你好，我想要轻松一点的，我177 130，夏天的衣服该怎么穿呢？男性`

检查：`OUTFIT_ADVICE`、`summer`、`casual`、`relaxed`、`177 cm / 65 kg`、换算提示、穿搭回答、候选快照、无矛盾 AI 标签。

- [ ] **Step 5: 最终中文提交**

```text
测试：补充自然语言穿搭修复回归验证
```
