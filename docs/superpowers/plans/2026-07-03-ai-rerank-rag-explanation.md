# AI Rerank And RAG Explanation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first working version of Java candidate pool + Python semantic rerank + RAG explanation observability.

**Architecture:** Java remains the source of product facts and candidate retrieval. Python ranks only Java-provided candidates, returns candidate-bound `product_refs`, exposes rerank debug, and uses RAG only as optional explanation context. RAG metadata is produced during vector rebuild and carried through debug when available.

**Tech Stack:** Java Spring Boot/MyBatis/JUnit, Python FastAPI/LangGraph/unittest, local JSON vector store.

## Global Constraints

- Python must never invent products outside Java `candidates`.
- RAG must not provide price, stock, SKU, or availability facts.
- RAG empty, weak, missing, or stale must not block recommendation when Java `candidates` exist.
- First version uses deterministic rerank rules; LLM preference mapping remains optional.
- Tests must be written before production code for each behavior change.

---

## File Structure

Python project: `/Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System`

- Modify `clothing_assistant/application/preference_parser.py`: map additional fuzzy terms such as “不要太正式”.
- Modify `clothing_assistant/application/recommendation_service.py`: expose rerank result with semantic preferences, candidate scores, selected refs, and optional RAG explanation clauses.
- Modify `clothing_assistant/application/answer_service.py`: place rerank debug fields into agent response debug.
- Modify `clothing_assistant/agent/nodes.py`: keep RAG-empty recommendation path working with Java candidates.
- Modify `clothing_assistant/infrastructure/vector_store.py`: write and read vector-store metadata.
- Modify `clothing_assistant/tools/rag_tool.py`: include `rag_meta` in RAG tool output.
- Test `tests/test_recommendation_service.py`: rerank debug, fuzzy preference, RAG explanation.
- Test `tests/test_agent_pipeline.py`: recommendation still completes when RAG is empty and debug identifies AI rerank source.
- Test `tests/test_rag_tool.py`: RAG meta flows through tool output.

Java project: `/Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem`

- Verify existing `AssistantService` candidate-bound filter tests.
- Add Java tests only if Python contract changes require Java DTO changes. First version should not require DTO changes.

---

### Task 1: Expose Python Rerank Result As A First-Class Contract

**Files:**
- Modify: `clothing_assistant/application/recommendation_service.py`
- Modify: `clothing_assistant/application/preference_parser.py`
- Test: `tests/test_recommendation_service.py`

**Interfaces:**
- Consumes: `parse_preferences(user_query: str) -> dict[str, Any]`
- Produces: `build_product_rerank_result(candidates, intent_result, user_query, user_context, tool_results, limit=3) -> dict[str, Any]`
- Produces: result keys `product_refs`, `semantic_preferences`, `candidate_scores`, `recommendation_source`

- [ ] **Step 1: Write failing rerank debug test**

Add this test to `tests/test_recommendation_service.py`:

```python
def test_build_product_rerank_result_exposes_preferences_scores_and_source(self):
    from clothing_assistant.application.recommendation_service import build_product_rerank_result

    candidates = [
        {
            "spu_id": 1001,
            "sku_id": 2001,
            "name": "基础通勤夹克",
            "category": "外套",
            "color": "黑色",
            "stock_status": "in_stock",
            "style_tags": ["commute", "casual", "basic"],
            "attribute_tags": ["适用场景:通勤", "风格:基础款"],
            "sale_price": 269,
        },
        {
            "spu_id": 1002,
            "sku_id": 2002,
            "name": "正式商务西装",
            "category": "西装",
            "color": "黑色",
            "stock_status": "in_stock",
            "style_tags": ["formal"],
            "attribute_tags": ["风格:商务正装"],
            "sale_price": 899,
        },
    ]

    result = build_product_rerank_result(
        candidates,
        {"intent": "recommendation"},
        "推荐一件300以内适合学生党通勤、不要太正式的外套",
        {},
        {},
    )

    self.assertEqual(result["recommendation_source"], "java_candidates_with_ai_rerank")
    self.assertEqual(result["semantic_preferences"]["budget_max"], 300)
    self.assertIn("student", result["semantic_preferences"]["persona_tags"])
    self.assertIn("commute", result["semantic_preferences"]["scene"])
    self.assertIn("overly_formal", result["semantic_preferences"]["avoid_tags"])
    self.assertEqual(result["product_refs"][0]["spu_id"], 1001)
    self.assertEqual(result["candidate_scores"][0]["spu_id"], 1001)
    self.assertGreater(result["candidate_scores"][0]["rank_score"], result["candidate_scores"][1]["rank_score"])
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
.venv/bin/python -m unittest tests.test_recommendation_service.RecommendationServiceTests.test_build_product_rerank_result_exposes_preferences_scores_and_source
```

Expected: FAIL because `build_product_rerank_result` does not exist.

- [ ] **Step 3: Implement minimal rerank result**

Add `build_product_rerank_result` next to `build_product_refs` in `recommendation_service.py`. Keep `build_product_refs` as a wrapper returning only `result["product_refs"]`.

- [ ] **Step 4: Add “不要太正式” preference mapping**

In `preference_parser.py`, add deterministic signals that map “不要太正式 / 别太正式 / 不想太成熟 / 不商务” to:

```python
avoid_tags=["overly_formal"]
style_tags=["casual", "minimal"]
```

- [ ] **Step 5: Run test to verify it passes**

Run the same unittest command. Expected: PASS.

---

### Task 2: Add Rerank Debug To Agent Responses

**Files:**
- Modify: `clothing_assistant/application/answer_service.py`
- Modify: `clothing_assistant/agent/nodes.py`
- Test: `tests/test_agent_pipeline.py`

**Interfaces:**
- Consumes: `build_product_rerank_result(...)`
- Produces debug keys: `semantic_preferences`, `candidate_scores`, `selected_product_refs`, `recommendation_source`

- [ ] **Step 1: Write failing agent debug test**

Extend `test_recommendation_uses_java_candidates_when_rag_is_empty` in `tests/test_agent_pipeline.py` with:

```python
self.assertEqual(result["debug"]["recommendation_source"], "java_candidates_with_ai_rerank")
self.assertIn("semantic_preferences", result["debug"])
self.assertIn("candidate_scores", result["debug"])
self.assertEqual(result["debug"]["selected_product_refs"], result["product_refs"])
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
.venv/bin/python -m unittest tests.test_agent_pipeline.AgentPipelineTests.test_recommendation_uses_java_candidates_when_rag_is_empty
```

Expected: FAIL because debug fields are missing.

- [ ] **Step 3: Implement debug fields**

Update `build_agent_response` in `answer_service.py` to call `build_product_rerank_result`, return `product_refs`, and include rerank fields in `debug`.

- [ ] **Step 4: Keep node recommendation draft candidate-bound**

Update `nodes.py` helper code to use `build_product_rerank_result` or keep `build_product_refs` wrapper without changing behavior. Do not allow RAG empty route to fall back when rerank refs exist.

- [ ] **Step 5: Run test to verify it passes**

Run the same unittest command. Expected: PASS.

---

### Task 3: Add RAG Metadata And Explanation Clauses

**Files:**
- Modify: `clothing_assistant/infrastructure/vector_store.py`
- Modify: `clothing_assistant/tools/rag_tool.py`
- Modify: `clothing_assistant/application/recommendation_service.py`
- Test: `tests/test_rag_tool.py`
- Test: `tests/test_recommendation_service.py`

**Interfaces:**
- Produces: `load_vector_store_meta() -> dict[str, Any]`
- Produces RAG result key: `rag_meta`
- Consumes: `tool_results["rag_tool"]["retrieved_chunks"]`

- [ ] **Step 1: Write failing RAG meta test**

Add this test to `tests/test_rag_tool.py`:

```python
def test_rag_tool_includes_vector_store_meta_when_available(self):
    with patch(
        "clothing_assistant.tools.rag_tool.search_similar_chunks",
        return_value=[],
    ), patch(
        "clothing_assistant.tools.rag_tool.load_vector_store_meta",
        return_value={"version": "2026-07-03T18:30:00+08:00", "chunk_count": 12},
    ):
        result = run_rag_tool("推荐一件通勤外套", query_type="recommendation")

    self.assertEqual(result["rag_meta"]["version"], "2026-07-03T18:30:00+08:00")
    self.assertEqual(result["rag_meta"]["chunk_count"], 12)
```

- [ ] **Step 2: Run RAG meta test to verify it fails**

Run:

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
.venv/bin/python -m unittest tests.test_rag_tool.RagToolTests.test_rag_tool_includes_vector_store_meta_when_available
```

Expected: FAIL because `load_vector_store_meta` is not imported or result lacks `rag_meta`.

- [ ] **Step 3: Implement vector store metadata**

In `vector_store.py`, define `VECTOR_STORE_META_FILE = VECTOR_DB_DIR / "vector_store_meta.json"`, write meta in `rebuild_vector_store`, and expose `load_vector_store_meta`.

- [ ] **Step 4: Add `rag_meta` to RAG tool output**

Update `run_rag_tool` to return `rag_meta` from `load_vector_store_meta()`. If meta file is missing, return `{}`.

- [ ] **Step 5: Write failing RAG explanation test**

Add this test to `tests/test_recommendation_service.py`:

```python
def test_rag_chunks_enrich_reason_without_overriding_candidate_facts(self):
    candidates = [
        {
            "spu_id": 1001,
            "sku_id": 2001,
            "name": "通勤轻薄外套",
            "category": "外套",
            "color": "黑色",
            "stock_status": "in_stock",
            "style_tags": ["commute"],
            "sale_price": 299,
        }
    ]

    refs = build_product_refs(
        candidates,
        {"intent": "recommendation"},
        "推荐一件通勤外套",
        {},
        {
            "rag_tool": {
                "retrieved_chunks": [
                    {
                        "file_name": "颜色选择.txt",
                        "content": "通勤场景优先选择黑色、灰色、藏青色等基础色，更容易搭配。",
                        "score": 0.1,
                    }
                ]
            }
        },
    )

    self.assertIn("RAG 知识提示", refs[0]["reason"])
    self.assertIn("通勤场景优先选择黑色", refs[0]["reason"])
    self.assertIn("价格 299", refs[0]["reason"])
```

- [ ] **Step 6: Run RAG explanation test to verify it fails**

Run the specific unittest. Expected: FAIL because reasons do not include RAG explanation.

- [ ] **Step 7: Implement safe RAG explanation clause**

Update recommendation reason construction to append a short `RAG 知识提示：...` clause from accepted `retrieved_chunks`. Do not parse or use price, SKU, stock, or availability from chunk content.

- [ ] **Step 8: Run both tests to verify they pass**

Run:

```bash
.venv/bin/python -m unittest tests.test_rag_tool tests.test_recommendation_service
```

Expected: PASS.

---

### Task 4: Verify Java Contract And Full Python Slice

**Files:**
- Verify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Verify: Python tests listed below

**Interfaces:**
- Java must continue filtering Python `product_refs` against current `candidates`.
- Python `/chat` debug may include extra fields, but `/chat/stream` done payload must not expose debug.

- [ ] **Step 1: Run targeted Python tests**

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
.venv/bin/python -m unittest tests.test_agent_pipeline tests.test_rag_tool tests.test_recommendation_service tests.test_api tests.test_chat_stream
```

Expected: all tests pass.

- [ ] **Step 2: Run targeted Java tests**

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh mvnw -Dtest=AssistantServiceTests,AssistantContextServiceTests,ProductCatalogServiceTests test
```

Expected: all tests pass.

- [ ] **Step 3: Commit Python implementation**

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
git add clothing_assistant/application/preference_parser.py clothing_assistant/application/recommendation_service.py clothing_assistant/application/answer_service.py clothing_assistant/agent/nodes.py clothing_assistant/infrastructure/vector_store.py clothing_assistant/tools/rag_tool.py tests/test_recommendation_service.py tests/test_agent_pipeline.py tests/test_rag_tool.py
git commit -m "feat: expose ai rerank debug and rag metadata"
```

- [ ] **Step 4: Commit Java plan if not already committed**

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem
git add docs/superpowers/plans/2026-07-03-ai-rerank-rag-explanation.md
git commit -m "docs: plan ai rerank rag explanation implementation"
```
