# Shared Contract Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create one shared Java-Python assistant contract source and make both projects verify their local DTO/schema fields against it.

**Architecture:** Add a sibling `outfit-project-contract` package containing human-readable boundary docs plus a small machine-readable field manifest for the Java-to-Python chat contract. Java and Python keep their local runtime DTOs, but tests read the shared manifest so field drift fails fast.

**Tech Stack:** Java 21, JUnit 5, Jackson, Python 3, unittest, Pydantic.

## Global Constraints

- Java remains the source of truth for users, sessions, products, SKUs, prices, inventory, orders, payments, and frontend APIs.
- Python must not invent product, price, inventory, order, or payment facts.
- Frontend and Python must not bypass Java to forge `user_context`, `candidates`, prices, inventory, order status, or payment status.
- Stage 2 does not add MQ; it hardens the existing synchronous Java-Python contract first.

---

## Files

- Create: `../outfit-project-contract/AGENTS.md`
- Create: `../outfit-project-contract/docs/business-rules.md`
- Create: `../outfit-project-contract/docs/coding-boundary.md`
- Create: `../outfit-project-contract/docs/dev-checklist.md`
- Create: `../outfit-project-contract/contracts/assistant-streaming-chat/v1.md`
- Create: `../outfit-project-contract/contracts/java-python-chat/v1.fields.json`
- Create: `AI-Clothing-Shopping-Assistant-System/tests/test_shared_contract.py`
- Create: `IntelligentOutfitRecommendationSystem/backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java`

## Task 1: Add Cross-Project Contract Tests

**Interfaces:**
- Consumes: `../outfit-project-contract/contracts/java-python-chat/v1.fields.json`
- Produces: failing tests that name the missing shared field manifest

- [x] **Step 1: Write Python failing test**

```bash
cd ../AI-Clothing-Shopping-Assistant-System
./.venv/bin/python -m unittest tests.test_shared_contract -v
```

Expected before manifest exists: failure explaining the shared contract file is missing.

- [x] **Step 2: Write Java failing test**

```bash
cd backend
sh ./mvnw -q -Dtest=SharedJavaPythonContractTests test
```

Expected before manifest exists: failure explaining the shared contract file is missing.

## Task 2: Create Shared Contract Package

**Interfaces:**
- Produces: `field_sets` in `v1.fields.json` for request, nested request models, Python response, Java-consumed response, product refs, and suggested actions

- [x] **Step 1: Add docs referenced by both existing AGENTS files**

Create the shared `AGENTS.md` and supporting docs so future agents can follow the same boundary rules without discovering a missing path.

- [x] **Step 2: Add the field manifest**

The manifest must include these exact field sets:

```text
python_chat_request
chat_history_item
user_context
product_candidate
python_chat_response
java_consumed_chat_response
product_ref
suggested_action
```

- [x] **Step 3: Re-run both contract tests**

Expected: both pass.

## Task 3: Regression Verification

- [x] **Step 1: Run Python contract and API tests**

```bash
./.venv/bin/python -m unittest tests.test_shared_contract tests.test_api tests.test_chat_stream -v
```

- [x] **Step 2: Run Java assistant contract tests**

```bash
sh ./mvnw -q -Dtest=SharedJavaPythonContractTests,RestPythonAssistantClientTests,AssistantServiceTests,PythonSseEventParserTests test
```

- [x] **Step 3: Run final Python full suite**

```bash
./.venv/bin/python -m unittest discover -v
```

## Completion Criteria

- Both Java and Python fail if `ProductCandidate`, `UserContext`, request, or response field names drift from the shared manifest.
- Both existing AGENTS files now point to a real `outfit-project-contract` directory.
- The next stage can focus on reproducible environment/config/CI instead of guessing contract fields manually.
