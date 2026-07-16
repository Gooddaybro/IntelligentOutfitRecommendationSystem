# Hybrid Demand Intent Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and merge the current shopping demand per conversation so short inputs such as “男性”, “黑色”, and “那女性呢？” immediately drive the correct SQL-filtered candidate set, while Python trusts Java's effective intent and failures degrade safely.

**Architecture:** Java remains the source of truth: deterministic parsing produces a per-turn patch, MySQL atomically stores the transition and current effective snapshot, and candidate SQL consumes only that snapshot. Python receives the effective snapshot for routing/reranking and never overrides Java hard filters; the frontend renders the returned snapshot and refreshes candidates from it.

**Tech Stack:** Java 21, Spring Boot, MyBatis, Flyway, MySQL/H2 tests, Jackson, Python 3.12, pytest, React/TypeScript, Vitest.

---

## File map

- `backend/src/main/java/.../assistant/dto/DemandIntentPatch.java`: one-turn slot patch and state action.
- `backend/src/main/java/.../assistant/service/DemandIntentResolver.java`: deterministic patch parsing, including gender switch/clear/compare/reset.
- `backend/src/main/java/.../assistant/service/DemandIntentMerger.java`: pure, ordered state merge.
- `backend/src/main/java/.../conversation/model/ChatDemandState.java`: persisted snapshot row.
- `backend/src/main/java/.../conversation/mapper/ConversationMapper.java` and `backend/src/main/resources/mapper/conversation/ConversationMapper.xml`: state/transition persistence and optimistic update.
- `backend/src/main/java/.../conversation/service/ConversationApplicationService.java`: idempotent transactional transition application.
- `backend/src/main/java/.../assistant/service/AssistantContextService.java`: resolve patch, apply state, and query candidates from the effective intent.
- `backend/src/main/resources/db/migration/V20__chat_demand_state.sql`: snapshot and transition schema.
- `clothing_assistant/agent/router.py` and `clothing_assistant/agent/agent_executor.py`: route valid Java demand intent as recommendation/refinement.
- `backend/src/main/java/.../assistant/service/AssistantFallbackService.java` and `AssistantService.java`: return a safe completed fallback response rather than exposing a raw stream error.
- `frontend/src/features/assistant/ChatPanel.tsx`: render and query from the server's effective snapshot.

### Task 1: Deterministic patch and merge semantics

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DemandIntentPatch.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentMerger.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentMergerTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentResolverTests.java`

- [x] **Step 1: Write failing tests for initialize, merge, switch, clear, compare, reset, and male/female alternation**

```java
assertThat(merger.merge(previous, resolver.resolvePatch(request("那女性呢？"))).targetGender())
        .isEqualTo("female");
assertThat(result.category()).isEqualTo("外套");
assertThat(merger.merge(result, resolver.resolvePatch(request("还是看看男性"))).targetGender())
        .isEqualTo("male");
```

- [x] **Step 2: Run tests and verify RED**

Run: `./mvnw.cmd -Dtest=DemandIntentMergerTests,DemandIntentResolverTests test`
Expected: FAIL because `DemandIntentPatch` and `DemandIntentMerger` do not exist.

- [x] **Step 3: Implement the minimum patch contract and ordered merge**

```java
public record DemandIntentPatch(String action, String rawQuery, String targetGender,
        boolean clearTargetGender, String category, List<String> scene,
        List<String> style, Integer budgetMax, List<String> attributes) {}
```

`compare` returns the previous snapshot unchanged; `clear` clears gender; `reset` starts empty; scalar non-null values overwrite; non-empty lists merge without duplicates; the new raw query is retained.

- [x] **Step 4: Run tests and verify GREEN**

Run: `./mvnw.cmd -Dtest=DemandIntentMergerTests,DemandIntentResolverTests test`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat: merge short demand intent patches"
```

### Task 2: Persist effective state and transitions

**Files:**
- Create: `backend/src/main/resources/db/migration/V20__chat_demand_state.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatDemandState.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/mapper/ConversationMapper.java`
- Modify: `backend/src/main/resources/mapper/conversation/ConversationMapper.xml`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationApplicationService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationDemandStateTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`

- [x] **Step 1: Write failing mapper/service tests for first insert, versioned update, request-id replay, and transition audit**

```java
DemandIntent first = service.applyDemandPatch(userId, threadId, "req-1", messageId, patch);
DemandIntent replay = service.applyDemandPatch(userId, threadId, "req-1", messageId, patch);
assertThat(replay).isEqualTo(first);
assertThat(jdbc.queryForObject("select count(*) from chat_demand_transition where request_id='req-1'", Integer.class)).isOne();
```

- [x] **Step 2: Run tests and verify RED**

Run: `./mvnw.cmd -Dtest=ConversationDemandStateTests,MySqlFlywayMigrationTests test`
Expected: FAIL because V20 tables and persistence methods are absent.

- [x] **Step 3: Add V20 schema with database-enforced identity and ownership**

```sql
CREATE TABLE chat_demand_state (
  session_id BIGINT PRIMARY KEY,
  state_version BIGINT NOT NULL DEFAULT 0,
  effective_intent_json LONGTEXT NOT NULL,
  last_request_id VARCHAR(64) NULL,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_chat_demand_state_session FOREIGN KEY (session_id) REFERENCES chat_session(id)
);
CREATE TABLE chat_demand_transition (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id BIGINT NOT NULL,
  message_id BIGINT NULL,
  request_id VARCHAR(64) NOT NULL,
  action VARCHAR(16) NOT NULL,
  patch_json LONGTEXT NOT NULL,
  effective_intent_json LONGTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uk_chat_demand_transition_request (session_id, request_id)
);
```

- [x] **Step 4: Implement transactional idempotent apply with one bounded optimistic retry**

Serialize `DemandIntentPatch`/`DemandIntent` with the existing Jackson `ObjectMapper`; on duplicate `requestId`, deserialize the stored transition result; update with `WHERE state_version = #{expectedVersion}` and retry once after reload if the row changed.

- [x] **Step 5: Run tests and verify GREEN**

Run: `./mvnw.cmd -Dtest=ConversationDemandStateTests,MySqlFlywayMigrationTests test`
Expected: PASS.

- [x] **Step 6: Commit**

```bash
git add backend/src/main/resources/db/migration/V20__chat_demand_state.sql backend/src/main/java backend/src/main/resources/mapper backend/src/test/java
git commit -m "feat: persist conversation demand state"
```

### Task 3: Use the effective snapshot for candidate SQL

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/MessageResponse.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

- [x] **Step 1: Write failing tests proving “男性→黑色→500以内→那女性呢？” preserves slots and queries female/unisex**

```java
assertThat(queryCaptor.getValue().getGender()).isEqualTo("female");
assertThat(queryCaptor.getValue().getBudgetMax()).isEqualTo(500);
assertThat(context.demandIntent().targetGender()).isEqualTo("female");
```

- [x] **Step 2: Run tests and verify RED**

Run: `./mvnw.cmd -Dtest=AssistantContextServiceTests,AssistantServiceTests test`
Expected: FAIL because context still resolves each message independently.

- [x] **Step 3: Apply the patch before candidate lookup and return the effective snapshot**

Pass the user-message id and request id into `buildContext`; use profile/body gender only while initializing an empty state, never after an explicit conversation gender exists. Build `RecommendationCandidateQuery` exclusively from the effective snapshot plus request-only fields not yet represented by `DemandIntent`.

- [x] **Step 4: Run focused SQL and service tests**

Run: `./mvnw.cmd -Dtest=AssistantContextServiceTests,AssistantServiceTests,RecommendationCandidateQueryServiceTests test`
Expected: PASS, including existing male/female/unisex constraints.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "feat: filter candidates from effective demand state"
```

### Task 4: Make Python trust Java demand intent

**Files (Python repository `D:/git/推荐系统/AI Clothing Shopping Assistant System`):**
- Modify: `clothing_assistant/agent/router.py`
- Modify: `clothing_assistant/agent/agent_executor.py`
- Test: `tests/test_recommendation_service.py`
- Test: `tests/test_agent_mvp.py`

- [x] **Step 1: Write failing tests for short gender input backed by Java intent**

```python
result = intent_router("男性", {"targetGender": "male", "hardFilters": ["targetGender"]})
assert result["intent"] == INTENT_RECOMMENDATION
```

- [x] **Step 2: Run tests and verify RED**

Run: `.venv/Scripts/python.exe -m pytest tests/test_agent_mvp.py tests/test_recommendation_service.py -q`
Expected: FAIL because `intent_router` ignores `demand_intent`.

- [x] **Step 3: Add the optional structured-intent input and use it only after execution-intent rules**

```python
def has_recommendation_demand(demand_intent):
    return any(demand_intent.get(key) for key in (
        "targetGender", "target_gender", "category", "budgetMax", "budget_max",
        "scene", "style", "attributes",
    ))
```

Policy/inventory/price/size signals keep their current priority; otherwise a valid Java demand routes to recommendation. `route_intent` passes `state.get("demand_intent")` into the router.

- [x] **Step 4: Run tests and verify GREEN**

Run: `.venv/Scripts/python.exe -m pytest tests/test_agent_mvp.py tests/test_recommendation_service.py -q`
Expected: PASS.

- [x] **Step 5: Commit only these files, preserving unrelated staged work**

```bash
git commit --only clothing_assistant/agent/router.py clothing_assistant/agent/agent_executor.py tests/test_agent_mvp.py tests/test_recommendation_service.py -m "fix: route Java demand intent as recommendation"
```

### Task 5: Complete stream degradation without raw error JSON

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

- [x] **Step 1: Write a failing stream test for Python/RAG failure**

Assert that a Python stream error produces a normal `done` event containing the effective intent and candidate count, a rule-based clarification answer, and no `python_stream_unavailable` error payload.

- [x] **Step 2: Run the test and verify RED**

Run: `./mvnw.cmd -Dtest=AssistantServiceTests#streamFailureCompletesWithCandidateFallback test`
Expected: FAIL because the handler currently emits an error event.

- [x] **Step 3: Reuse `chatFallbackResponse` to call `onDone` on stream failures**

The fallback answer must say that current conditions were applied and ask for category/scene/budget when only gender is known. Product refs remain empty, so no non-candidate product can be invented; the frontend still reloads Java candidates using the effective intent.

- [x] **Step 4: Run tests and verify GREEN**

Run: `./mvnw.cmd -Dtest=AssistantServiceTests test`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add backend/src/main/java backend/src/test/java
git commit -m "fix: degrade assistant streams to Java candidates"
```

### Task 6: Frontend snapshot behavior

**Files:**
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/features/assistant/assistantState.test.ts`

- [x] **Step 1: Add failing tests for effective-state replacement and cleared gender**

```ts
expect(requestFiltersFromResolvedIntent({ targetGender: undefined }, { gender: "male" })).not.toHaveProperty("gender");
```

The test also verifies a female snapshot replaces a prior male query without preserving stale gender.

- [x] **Step 2: Run tests and verify RED**

Run: `npm test -- --run src/features/assistant/assistantState.test.ts`
Expected: FAIL because fallback filters reintroduce cleared server slots.

- [x] **Step 3: Treat a returned snapshot as authoritative**

When `resolvedIntent` exists, construct category/style/budget/gender from it rather than falling back slot-by-slot to stale request filters. Preserve only request properties not represented by the snapshot, such as season until the contract exposes it.

- [x] **Step 4: Run tests and verify GREEN**

Run: `npm test -- --run src/features/assistant/assistantState.test.ts src/shared/api/assistantStream.test.ts`
Expected: PASS.

- [x] **Step 5: Commit**

```bash
git add frontend/src/features/assistant/ChatPanel.tsx frontend/src/features/assistant/assistantState.test.ts
git commit -m "fix: render authoritative demand snapshot"
```

### Task 7: Verification and documentation status

**Files:**
- Modify: `docs/superpowers/specs/2026-07-16-hybrid-demand-intent-refinement-design.md`

- [x] **Step 1: Run Java verification**

Run: `./mvnw.cmd verify`
Expected: all tests and Checkstyle pass.

- [x] **Step 2: Run frontend verification**

Run: `npm test -- --run && npm run build`
Expected: Vitest and TypeScript/Vite build pass.

- [x] **Step 3: Run Python verification**

Run: `.venv/Scripts/python.exe -m pytest -q && .venv/Scripts/python.exe -m ruff check clothing_assistant tests`
Expected: all Python tests and Ruff pass.

- [x] **Step 4: Mark the design implemented and record deliberately deferred scope**

Set the document status to “已实施（确定性 B+C 主链）”. Record that LLM-generated structured patches and manual catalog-wide gender calibration require a separate data/model rollout; deterministic slots, durable merging, Python consumption, SQL filtering, and graceful degradation are complete in this plan.

- [x] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-07-16-hybrid-demand-intent-refinement-design.md docs/superpowers/plans/2026-07-16-hybrid-demand-intent-refinement.md
git commit -m "docs: complete hybrid demand intent rollout"
```

