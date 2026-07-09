# Fuzzy Recommendation Intent Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Make fuzzy shopping phrases resolve into stable Java `DemandIntent` fields and make Python rerank consume that intent.

**Architecture:** Java remains the owner of product-safe intent resolution and candidate filtering. Python receives `demand_intent` as read-only state, merges it into existing semantic preferences, reranks only Java candidates, and never invents commerce facts.

**Tech Stack:** Java 17/Spring Boot/JUnit/Mockito/AssertJ, Python/Pydantic/unittest, no new dependencies.

## Global Constraints

- Do not add a new LLM Router.
- Do not add database fields.
- Do not let Python choose product IDs outside Java candidates.
- Do not change Java-Python field names; `demand_intent` already exists.
- Treat words without numeric budget, such as `别太贵`, as soft preferences, not `budgetMax`.

---

### Task 1: Java fuzzy intent rules

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`

**Interfaces:**
- Consumes: `AssistantChatRequest.message()`, existing `DemandIntent` record.
- Produces: `DemandIntent.scene()`, `DemandIntent.style()`, `DemandIntent.attributes()`, and `RecommendationCandidateQuery.season`.

- [x] **Step 1: Add failing Java tests**

Add tests for:

```java
@Test
void synonymWarmOuterwearDemandMapsToWinterOuterwearIntent() {
    AssistantChatRequest request = new AssistantChatRequest(
            null, "有没有不容易冷的外套", null, null, null, null, null, null
    );
    // assert candidate query category=外套, season=winter
    // assert demandIntent attributes contains 保暖
}

@Test
void studentDailyBudgetVisualDemandStaysSoftWithoutNumericBudget() {
    AssistantChatRequest request = new AssistantChatRequest(
            null, "大学生日常上课，别太贵，还要遮肉显腿长", null, null, null, null, null, null
    );
    // assert budgetMax is null
    // assert scene contains campus,daily
    // assert attributes contains 平价,显瘦,显高
}
```

- [x] **Step 2: Run Java tests and verify red**

Run:

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -q -Dtest=AssistantContextServiceTests test
```

Expected: the new tests fail because some synonyms are not mapped yet.

- [x] **Step 3: Implement minimal Java rules**

Extend existing arrays and helper branches in `DemandIntentResolver` and `AssistantContextService`; do not create a DSL or new parser class.

- [x] **Step 4: Run Java tests and verify green**

Run the same Maven command. Expected: `AssistantContextServiceTests` passes.

---

### Task 2: Python demand_intent propagation and rerank merge

**Files:**
- Modify: `../AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/app.py`
- Modify: `../AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/langgraph_executor.py`
- Modify: `../AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/state.py`
- Modify: `../AI-Clothing-Shopping-Assistant-System/clothing_assistant/application/answer_service.py`
- Modify: `../AI-Clothing-Shopping-Assistant-System/clothing_assistant/application/recommendation_service.py`
- Test: `../AI-Clothing-Shopping-Assistant-System/tests/test_recommendation_service.py`

**Interfaces:**
- Consumes: Python request field `demand_intent`, dumped with `model_dump(exclude_none=True, exclude_unset=True)`.
- Produces: merged rerank preferences in `build_product_rerank_result()["semantic_preferences"]`.

- [x] **Step 1: Add failing Python test**

Add a test that calls `build_product_rerank_result(..., demand_intent={...})` and asserts Java intent affects rerank even when `user_query` itself is vague.

```python
result = build_product_rerank_result(
    candidates,
    {"intent": "recommendation"},
    "看看这个",
    {},
    {},
    demand_intent={
        "scene": ["campus", "daily"],
        "style": ["casual"],
        "attributes": ["平价", "显瘦", "显高"],
    },
)
```

- [x] **Step 2: Run Python test and verify red**

Run:

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_recommendation_service -v
```

Expected: new test errors because `build_product_rerank_result` does not accept `demand_intent` yet, or fails because intent is ignored.

- [x] **Step 3: Thread demand_intent through the existing path**

Add optional `demand_intent=None` parameters to `run_langgraph_agent`, initial state, `build_agent_response`, `build_product_refs`, and `build_product_rerank_result`. Pass `request.demand_intent` from `api/app.py`.

- [x] **Step 4: Merge Java intent into existing preferences**

Add one small helper in `recommendation_service.py` that maps Java intent to existing preference keys, then merge with `parse_preferences(user_query)`.

- [x] **Step 5: Run Python tests and verify green**

Run the same Python unittest command. Expected: recommendation service tests pass.

---

### Task 3: Final verification and commit

**Files:**
- All files from Tasks 1 and 2.

**Interfaces:**
- Produces: committed implementation on the current branch.

- [x] **Step 1: Run Java verification**

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -q -Dtest=AssistantContextServiceTests test
```

- [x] **Step 2: Run Python verification**

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_recommendation_service -v
```

- [x] **Step 3: Review diff**

```bash
git -C /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem diff --stat
git -C /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System diff --stat
```

- [x] **Step 4: Commit changed files**

Commit Java repo changes and Python repo changes in their own repositories if both verifications pass.
