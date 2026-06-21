# AI Commerce Quality Closure Implementation Plan

> **Status:** Phase 1 through Phase 6 implemented on 2026-06-20. Current next step is broad regression hardening or deployment preparation.

**Goal:** Turn the current AI clothing shopping system from "feature-complete demo" into a verifiable engineering project. The next work should close quality gaps around AI answer reliability, end-to-end shopping flows, Java/Python contract stability, recommendation explanations, frontend state boundaries, and production safety.

**Architecture:** Java remains the source of truth for users, products, SKUs, prices, inventory, cart, orders, payment, and frontend APIs. Python remains responsible for intent recognition, RAG answers, recommendation orchestration, ranking explanations, and natural language generation. The frontend can present recommendations and collect user confirmation, but it must not forge prices, inventory, order state, payment state, or user ownership.

**Approval rule:** This document remains the approval checklist for remaining phases. Development of the next phase starts after the owner explicitly agrees which phase to implement.

---

## Current Baseline

Already strong:

- Java backend has API, service, mapper, migration, and Testcontainers-style coverage.
- Python AI service has LangGraph routing tests, API tests, eval report tests, and data-boundary documentation.
- Frontend has typed API clients, SSE parsing tests, commerce action tests, and manual integration documentation.
- The system already separates Java business facts from Python AI reasoning.

Remaining gaps after Phase 6:

- AI answer-quality coverage now exists, but can be expanded with more real shopping prompts.
- The user-facing shopping journey now has a mocked browser smoke test, but can be expanded against a live local backend.
- Java/Python contract regression coverage now exists for the highest-risk recommendation and SSE paths, but can be broadened as the contract grows.
- Production safety defaults now have first regression coverage. Remaining work is to broaden checks as new integrations are added.

## Phase 1: AI Answer Quality Evaluation

**Owner project:** `AI-Clothing-Shopping-Assistant-System`

**Purpose:** Separate "the graph selected the right tools" from "the final answer is correct, grounded, and safe for a shopper."

**Current status:** implemented. The Python project now has deterministic answer-quality cases, a report runner, and unit tests covering inventory, price, missing information, RAG fallback, debug-leak prevention, and size-history behavior.

**Detailed document:**

- `../AI-Clothing-Shopping-Assistant-System/docs/answer-quality-evaluation-plan.md`

**Expected files after approval:**

- `clothing_assistant/agent/answer_quality_cases.py`
- `clothing_assistant/agent/answer_quality_report.py`
- `tests/test_answer_quality_report.py`
- Updates to `docs/eval-plan.md`

**Acceptance criteria:**

- Inventory answers must include exact product, color, size, and stock facts when those facts are available.
- Price answers must use structured catalog facts, not RAG or generated guesses.
- Missing product/color/size cases must ask a follow-up instead of guessing.
- Weak RAG evidence must produce conservative fallback answers.
- Debug JSON and internal trace details must not appear in user-visible answers.
- Report output should clearly show passed, failed, and skipped cases.

**Recommended first implementation batch:**

- 8 to 12 deterministic answer-quality cases.
- Rule-based scoring only; do not introduce an LLM judge yet.

**Verification commands used:**

```bash
.venv/bin/python -m unittest tests.test_answer_quality_report -v
.venv/bin/python -m clothing_assistant.agent.answer_quality_report
.venv/bin/python -m unittest discover -v
.venv/bin/python -m compileall -q clothing_assistant tests
```

## Phase 2: Frontend E2E Quality Gate

**Owner project:** `IntelligentOutfitRecommendationSystem`

**Purpose:** Automatically verify the complete shopping journey that a real user cares about.

**Current status:** first Playwright smoke flow implemented with API mocks. It verifies login, AI streaming recommendation, confirmation-gated add-to-cart, cart checkout, order creation, and mock payment UI flow.

**Detailed document:**

- `docs/superpowers/specs/2026-06-19-frontend-e2e-quality-gate-design.md`

**Expected files after approval:**

- `frontend/playwright.config.ts`
- `frontend/e2e/ai-shopping.spec.ts`
- `frontend/e2e/fixtures/api.ts`
- `frontend/package.json` script: `test:e2e`
- Optional frontend test fixtures under `frontend/e2e/fixtures/`
- Optional updates to `docs/frontend-backend-integration-test.md`

**Acceptance criteria:**

- A browser test can register or log in a test user.
- The AI shopping page can submit a prompt and render a response or controlled fallback.
- Recommendation cards remain tied to backend product/SKU data.
- Add-to-cart and buy-now actions require explicit confirmation.
- Cart checkout and mock payment can be verified through UI state and backend responses.
- Tests can run locally without depending on real model availability.

**Recommended first implementation batch:**

- One smoke E2E spec using mocked assistant responses or deterministic backend test mode.
- One authenticated commerce flow spec against local Java backend.

## Phase 3: Java/Python Contract Regression Tests

**Owner projects:** both

**Purpose:** Prevent accidental breakage in cross-service data shape, especially streaming events and product references.

**Current status:** implemented first regression batch. Java now verifies sync and SSE assistant responses only expose Python product refs that match Java-controlled candidates. Python now verifies `/chat/stream` emits single-line JSON SSE payloads, keeps `product_refs` in the done event, and does not expose debug internals in stream output.

**Expected files after approval:**

- Java tests around `/api/assistant/chat/stream` forwarding and fallback behavior.
- Python tests around `/chat/stream` SSE event shape.
- Shared examples in contract documentation.

**Acceptance criteria:**

- SSE events remain single-line JSON payloads.
- Supported event types remain stable: `thread`, `token`, `recommendation`, `done`, `error`.
- `recommendedSpuIds` from Python are filtered or ordered against Java-controlled candidates.
- Python must not return product refs that cannot be traced to Java-provided candidates.
- External service failures return diagnosable but non-sensitive errors.

**Implemented regression points:**

- `AssistantControllerTests` covers Java `/api/assistant/chat` and `/api/assistant/chat/stream` with one valid product ref and one hallucinated product ref. Only the valid Java candidate is exposed as `recommendedSpuIds`.
- `AssistantServiceTests` covers stream failure handling and confirms Java does not persist a fake assistant message when the Python stream fails.
- `tests/test_chat_stream.py` covers Python `/chat/stream` done-event payload shape, JSON line format, `product_refs`, and debug-field suppression.
- `tests/test_recommendation_service.py` covers Python `product_refs` generation so invalid, missing, and duplicate candidate ids are skipped.

**Verification commands used:**

```bash
# Java backend
cd backend
bash ./mvnw test -Dtest=AssistantControllerTests,AssistantServiceTests,RestPythonAssistantClientTests,PythonSseEventParserTests

# Python AI service
.venv/bin/python -m unittest tests.test_chat_stream tests.test_recommendation_service -v
```

## Phase 4: Recommendation Explanation

**Owner projects:** both

**Purpose:** Let users see why a product was recommended instead of receiving a black-box product card.

**Current status:** implemented. Python now produces traceable recommendation reasons from candidate data and user context. Java exposes filtered `recommendedItems` alongside the legacy `recommendedSpuIds`. The frontend renders the reason and rank score on recommendation cards. The missing local product image issue was also fixed in this phase by adding SVG assets and migrating seeded image URLs from `.jpg` to `.svg`.

**Implemented behavior:**

- Python produces explanation metadata based on allowed evidence.
- Java controls product facts and can pass candidate attributes.
- Frontend renders concise recommendation reasons such as style match, budget fit, season fit, available color, or size confidence.
- Product seed image URLs point to static assets that exist in both backend resources and frontend public assets.

**Acceptance criteria:**

- Explanations do not invent inventory, price, discounts, or policies.
- Each reason is traceable to either Java candidate data, structured Python catalog facts, or accepted RAG evidence.
- Recommendation cards remain usable without explanations if AI service is unavailable.

**Implemented files:**

- Java response contract: `AssistantRecommendationItem`, `AssistantChatResponse`, `AssistantStreamDoneEvent`, `AssistantService`.
- Java seed/static images: `V9__use_local_svg_product_images.sql`, `backend/src/main/resources/static/images/products/*.svg`.
- Python explanation scoring: `clothing_assistant/application/recommendation_service.py`.
- Frontend rendering: `frontend/src/shared/api/types.ts`, `frontend/src/shared/api/assistantStream.ts`, `frontend/src/features/assistant/ChatPanel.tsx`, `frontend/src/features/catalog/ProductCard.tsx`, `frontend/src/styles.css`.
- Frontend static images: `frontend/public/images/products/*.svg`.

**Verification commands used:**

```bash
# Java backend
cd backend
bash ./mvnw test -Dtest=AssistantControllerTests,AssistantServiceTests,ProductControllerTests
bash ./mvnw test

# Python AI service
.venv/bin/python -m unittest tests.test_recommendation_service tests.test_chat_stream -v
.venv/bin/python -m unittest discover -v
.venv/bin/python -m compileall -q clothing_assistant tests

# Frontend
cd frontend
npm test -- --run
npm run build
npm run test:e2e
```

## Phase 5: Frontend State Boundary Cleanup

**Owner project:** `IntelligentOutfitRecommendationSystem`

**Purpose:** Reduce growth pressure on `frontend/src/app/App.tsx` before adding more workflows.

**Current status:** implemented. `App.tsx` now composes focused state hooks instead of directly owning auth, cart, assistant, recommendation, and pending commerce-action state. The first implementation stays inside React hooks and reducers; no new state-management dependency was introduced.

**Implemented approach:**

- Keep server data in API hooks or a small query layer.
- Move auth state, cart state, and assistant state into focused modules.
- Use `useReducer` for assistant chat/recommendation state transitions.
- Keep confirmed commerce actions in a focused hook so add-to-cart and buy-now stay behind explicit confirmation.
- Do not introduce Zustand or TanStack Query yet; current complexity does not justify extra dependency surface.

**Acceptance criteria:**

- Auth, cart, assistant chat, recommendation state, and pending commerce action each have clear ownership.
- Existing UI behavior remains unchanged.
- Existing frontend tests still pass, and new tests cover reducer/store transitions.

**Implemented files:**

- `frontend/src/features/auth/useAuthSession.ts`
- `frontend/src/features/cart/useCartState.ts`
- `frontend/src/features/assistant/assistantState.ts`
- `frontend/src/features/assistant/assistantState.test.ts`
- `frontend/src/features/commerce-action/useCommerceAction.ts`
- `frontend/src/app/App.tsx`

**Verification commands used:**

```bash
cd frontend
npm test -- --run
npm run build
```

## Phase 6: Production Safety Defaults

**Owner projects:** both

**Purpose:** Convert documented safety boundaries into defaults and checks.

**Current status:** implemented first safety-default batch. Java now returns fixed public messages for external-service and unexpected server failures, while logging only exception class names. Python validation errors no longer echo full request bodies or Pydantic `input` values. Python trace-to-file remains opt-in and now redacts obvious secret fragments before writing local JSONL traces.

**Implemented behavior:**

- Java `GlobalExceptionHandler` maps `ExternalServiceException` to a fixed `external_service_error` message.
- Java `GlobalExceptionHandler` catches unexpected exceptions and returns a fixed `internal_server_error` message.
- Python `/chat` and `/chat/stream` keep `debug=false` by default.
- Python 422 responses keep `request_id` when available, but do not echo `user_context`, `candidates`, raw prompt text, or validation `input`.
- Python SSE error events keep the existing fixed message and do not include stack traces or provider details.
- Python trace persistence is still controlled by `AGENT_TRACE_TO_FILE=true`; trace records redact obvious `Authorization: Bearer ...`, `api_key=...`, `token=...`, `password=...`, and `secret=...` fragments.

**Acceptance criteria:**

- `debug=false` is the default for user-facing calls.
- Trace-to-file is opt-in and does not capture secrets.
- Java/Python internal calls have a clear authentication or internal-token boundary.
- Error responses do not expose stack traces, prompt text, file paths, API keys, or raw model provider messages.
- Logs can be correlated by `request_id`, `session_id`, `thread_id`, or equivalent run id.

**Implemented files:**

- Java: `backend/src/main/java/.../common/error/GlobalExceptionHandler.java`
- Java tests: `backend/src/test/java/.../common/error/GlobalExceptionHandlerTests.java`
- Python: `clothing_assistant/api/app.py`
- Python: `clothing_assistant/agent/tracing.py`
- Python tests: `tests/test_api.py`, `tests/test_agent_pipeline.py`

**Verification commands used:**

```bash
# Java backend
cd backend
bash ./mvnw test -Dtest=GlobalExceptionHandlerTests,AssistantControllerTests

# Python AI service
.venv/bin/python -m unittest tests.test_api tests.test_chat_stream tests.test_agent_pipeline -v
```

## Development Order

Recommended order:

1. Phase 1: AI answer quality evaluation.
2. Phase 2: frontend E2E quality gate.
3. Phase 3: Java/Python contract regression tests.
4. Phase 4: recommendation explanation.
5. Phase 5: frontend state boundary cleanup.
6. Phase 6: production safety defaults.

Reasoning:

- Phase 1 and Phase 2 provide immediate quality visibility.
- Phase 3 protects the seam between the two services before more features depend on it.
- Phase 4 improves product value after correctness gates exist.
- Phase 5 is safer after tests exist.
- Phase 6 can be implemented incrementally, but should be checked before any deployment-like demo.

## Not In Scope Until Approved

- No runtime code changes.
- No database schema changes.
- No new external model provider.
- No real payment integration.
- No frontend redesign.
- No replacement of the current Java/Python responsibility boundary.

## Owner Decision Needed

Completed:

- Phase 1: AI answer quality evaluation.
- Phase 2: frontend E2E quality gate.
- Phase 3: Java/Python contract regression tests.
- Phase 4: recommendation explanation and local product image visibility fix.
- Phase 5: frontend state boundary cleanup.
- Phase 6: production safety defaults.

Next decision:

- Broaden regression coverage across Phases 1-6.
- Or prepare deployment/demo configuration with real environment variable overrides.
