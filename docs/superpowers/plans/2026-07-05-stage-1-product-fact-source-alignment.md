# Stage 1 Product Fact Source Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Python price and inventory answers use Java-provided candidates instead of the local demo product JSON for production chat requests.

**Architecture:** Add a candidate-backed structured lookup path inside the Python LangGraph node layer. Keep the existing JSON catalog helper as local demo/test support, but production request state with `candidates` should resolve product, color, size, price, and stock from those candidates.

**Tech Stack:** Python 3, FastAPI, LangGraph node functions, unittest.

## Global Constraints

- Java remains the source of truth for products, SKUs, prices, and inventory.
- Python must not invent product, price, inventory, order, payment, or user ownership facts.
- `/chat` and `/chat/stream` must keep their existing request and response shapes.
- Tests must prove the new production path fails before implementation and passes after implementation.

---

### Task 1: Candidate-Backed Structured Lookup Tests

**Files:**
- Modify: `AI-Clothing-Shopping-Assistant-System/tests/test_agent_pipeline.py`

**Interfaces:**
- Consumes: `run_langgraph_agent(query, candidates=[...])`
- Produces: regression coverage that price and inventory answers are grounded in Java candidates.

- [ ] **Step 1: Add failing tests**

Add tests that pass a Java-style `candidates` list with product facts that differ from local JSON demo data. The expected answer must include the candidate price or stock count, and must not include stale JSON values.

- [ ] **Step 2: Verify tests fail**

Run:

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_agent_pipeline.AgentPipelineTests.test_price_check_uses_java_candidate_price tests.test_agent_pipeline.AgentPipelineTests.test_inventory_check_uses_java_candidate_stock -v
```

Expected: both tests fail because `nodes.py` still calls `run_structured_lookup()` without passing candidates.

### Task 2: Candidate Lookup Runtime

**Files:**
- Modify: `AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/nodes.py`

**Interfaces:**
- Consumes: `state["candidates"]`, `state["user_query"]`, and `state["intent_result"]`
- Produces: `structured_result` with `lookup_type`, `matched_product_name`, `matched_product_id`, `sku`, `price_cny`, `stock_count`, `in_stock`, `color`, `size`, `missing_fields`, and `reason`

- [ ] **Step 1: Implement candidate matching helpers**

Add small local helpers in `nodes.py` so the production lookup can match by `name`, `spu_id`, `sku_id`, `spu_code`, or `sku_code`.

- [ ] **Step 2: Use candidates before JSON**

Change `run_catalog_lookup(state)` to use candidate-backed lookup when `state["candidates"]` is non-empty. Fall back to existing JSON lookup only when no candidates were supplied.

- [ ] **Step 3: Keep missing-info behavior conservative**

Update `missing_info_gate_node(state)` so candidate-backed price/inventory requests do not fail just because the local JSON catalog cannot match the product.

### Task 3: Verification

**Files:**
- Test: `AI-Clothing-Shopping-Assistant-System/tests/test_agent_pipeline.py`
- Test: `AI-Clothing-Shopping-Assistant-System/tests/test_recommendation_service.py`
- Test: `AI-Clothing-Shopping-Assistant-System/tests/test_api.py`

- [ ] **Step 1: Run focused tests**

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_agent_pipeline tests.test_recommendation_service tests.test_api -v
```

- [ ] **Step 2: Run compile check**

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m compileall -q clothing_assistant tests
```

- [ ] **Step 3: Report next stage**

After Stage 1 verification, report that Stage 2 is shared Java-Python contract governance.
