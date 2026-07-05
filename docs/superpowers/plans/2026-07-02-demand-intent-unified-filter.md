# DemandIntent Unified Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Java produce a single `DemandIntent` for each assistant request, use it for hard candidate filtering, pass it to Python for ranking explanation, and return it to the frontend for display and candidate refresh.

**Architecture:** Java owns `DemandIntent` resolution because Java owns product facts, filters, cache keys, and candidate pools. Python consumes `demand_intent` for ranking only; frontend displays `resolvedIntent` and no longer parses natural language into hard filters.

**Tech Stack:** Spring Boot records/services, MyBatis-backed candidate queries, Pydantic API schemas, Python deterministic ranking, React/TypeScript Vitest.

---

### Task 1: Contract and Golden Cases

**Files:**
- Create: `docs/contracts/demand-intent-v1-cases.json`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DemandIntent.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`

- [ ] **Step 1: Add golden cases**

Create `docs/contracts/demand-intent-v1-cases.json` with:

```json
[
  {
    "query": "女性裙子推荐",
    "expected": {
      "targetGender": "female",
      "category": "半裙",
      "scene": [],
      "style": [],
      "budgetMax": null,
      "hardFilters": ["targetGender", "category"]
    }
  },
  {
    "query": "男性穿搭",
    "expected": {
      "targetGender": "male",
      "category": null,
      "scene": [],
      "style": [],
      "budgetMax": null,
      "hardFilters": ["targetGender"]
    }
  },
  {
    "query": "女生上班通勤，预算500以内",
    "expected": {
      "targetGender": "female",
      "category": null,
      "scene": ["commute"],
      "style": ["commute", "minimal"],
      "budgetMax": 500,
      "hardFilters": ["targetGender", "budgetMax"]
    }
  }
]
```

- [ ] **Step 2: Add failing Java assertions**

Extend `AssistantContextServiceTests` so `buildContext(...)` captures `AssistantContext` or the query path and verifies `DemandIntent` exists with the expected fields for the golden cases.

- [ ] **Step 3: Run Java focused test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest="AssistantContextServiceTests" test
```

Expected: compile/test failure because `DemandIntent` is not wired into `AssistantContext` yet.

### Task 2: Java DemandIntent Resolver and Candidate Query

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantContext.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

- [ ] **Step 1: Implement minimal `DemandIntent` record**

Fields:

```java
String version;
String source;
String rawQuery;
String targetGender;
String category;
List<String> scene;
List<String> style;
Integer budgetMax;
List<String> attributes;
List<String> hardFilters;
List<String> softPreferences;
BigDecimal confidence;
List<String> missingSlots;
```

- [ ] **Step 2: Move existing message parsing into `DemandIntentResolver`**

Keep the current small rules: gender, skirt category alias, budget, commute scene/style, and a few visual attributes. No LLM and no new dependency.

- [ ] **Step 3: Build `RecommendationCandidateQuery` from `DemandIntent`**

`AssistantContextService` should call resolver once, then construct candidate query from `demandIntent.category()`, `demandIntent.style().get(0)` when available, `demandIntent.budgetMax()`, and `demandIntent.targetGender()`.

- [ ] **Step 4: Keep direct product candidate API alias normalization**

`ProductCatalogService.findRecommendationCandidates` should continue normalizing request category aliases such as `裙子 -> 半裙` for non-chat callers.

- [ ] **Step 5: Run focused Java tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest="AssistantContextServiceTests,ProductCatalogServiceTests" test
```

Expected: all focused tests pass.

### Task 3: Java-Python Contract and Python Ranking

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatRequest.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/api/schemas.py`
- Modify: `AI Clothing Shopping Assistant System/clothing_assistant/application/recommendation_service.py`
- Modify: `AI Clothing Shopping Assistant System/tests/test_api.py`
- Modify: `AI Clothing Shopping Assistant System/tests/test_recommendation_service.py`

- [ ] **Step 1: Add failing Java/Python contract tests**

Assert Java serializes `demand_intent` in `PythonChatRequest`, Python accepts it, and `build_product_refs` uses it to prefer matching category/scene candidates.

- [ ] **Step 2: Pass `demand_intent` from Java to Python**

Add `@JsonProperty("demand_intent") DemandIntent demandIntent` to `PythonChatRequest` and pass `context.demandIntent()`.

- [ ] **Step 3: Return `resolvedIntent` to frontend**

Add `DemandIntent resolvedIntent` to `AssistantChatResponse` and `AssistantStreamDoneEvent`.

- [ ] **Step 4: Add Python Pydantic `DemandIntent`**

Add a Pydantic model with Java-compatible snake_case aliases where needed, and expose `PythonChatRequest.demand_intent`.

- [ ] **Step 5: Use demand intent in Python ranking**

In `build_product_refs`, merge `demand_intent.scene/style/attributes/category/budgetMax` into existing query terms and budget resolution. Do not hard-filter candidates by Python-only rules.

- [ ] **Step 6: Run focused contract tests**

Run:

```powershell
.\mvnw.cmd -Dtest="AssistantServiceTests,AssistantControllerTests" test
python -m unittest tests.test_api tests.test_recommendation_service -v
```

Expected: focused tests pass, except any pre-existing error-message language assertions must be reported if unrelated.

### Task 4: Frontend Displays Resolved Intent

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/assistantStream.ts`
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/features/assistant/assistantState.test.ts`

- [ ] **Step 1: Add failing frontend tests**

Assert chat response and stream done events can carry `resolvedIntent`, and `ChatPanel` helper maps resolved intent to candidate request params.

- [ ] **Step 2: Add TypeScript `DemandIntent` type**

Add `DemandIntent` to `types.ts`, attach it to `AssistantChatResponse`, and to stream `done` event.

- [ ] **Step 3: Remove natural-language hard-filter inference from frontend**

Stop using `inferRequestFiltersFromMessage` for gender/category. Keep budget UI field handling only if explicit.

- [ ] **Step 4: Refresh candidates from resolved intent**

When `done` includes `resolvedIntent`, call `/api/products/recommendation-candidates` with `category`, `gender`, and `budgetMax` derived from `resolvedIntent`.

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
npm test -- --run
npm run build
```

Expected: tests and production build pass.

### Task 5: Final Verification

**Files:**
- No new runtime files.

- [ ] **Step 1: Run Java verification**

```powershell
.\mvnw.cmd verify
```

- [ ] **Step 2: Run Python focused verification**

```powershell
python -m unittest tests.test_api tests.test_chat_stream tests.test_recommendation_service tests.test_preference_parser -v
```

- [ ] **Step 3: Run frontend verification**

```powershell
npm test -- --run
npm run build
```

- [ ] **Step 4: Review diff**

```powershell
git diff --stat
git diff -- backend/src/main/java backend/src/test/java docs/contracts docs/superpowers frontend/src
```

Expected: only DemandIntent contract, resolver, DTO/schema, ranking, frontend display, and tests changed.
