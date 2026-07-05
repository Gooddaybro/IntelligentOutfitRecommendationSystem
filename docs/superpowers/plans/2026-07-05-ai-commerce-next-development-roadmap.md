# AI Commerce Next Development Roadmap

> **For agentic workers:** This document is the staged development roadmap. Before implementing any single stage, create or follow a focused implementation plan for that stage and verify both Java and Python sides where the change crosses services.

**Goal:** Turn the current AI clothing commerce demo into a more production-shaped engineering project by closing the highest-impact gaps one at a time.

**Architecture:** Keep the current modular Java monolith plus separate Python AI service. Java remains the source of truth for users, sessions, products, SKU, price, inventory, order, payment, and frontend APIs. Python remains the AI orchestration layer for intent recognition, recommendation ranking, RAG answers, and natural language generation.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis, Flyway, MySQL 8, Redis 7, React, TypeScript, Vite, Python FastAPI, LangGraph, unittest/Vitest/Playwright.

## Global Constraints

- Do not introduce MQ before the core fact-source and deployment gaps are closed.
- Do not let Python or the frontend invent product, price, inventory, order, payment, or user ownership facts.
- Keep `/chat` available while evolving `/chat/stream`.
- Prefer small, verifiable stages over broad infrastructure rewrites.
- Preserve current shopping flows: browse, AI recommendation, add to cart, create order, mock pay, and behavior recording.
- When changing Java-Python contracts, update both projects and run contract-focused tests.

---

## Current Status

The project already has the important foundations:

- Java backend modules for auth, user profile, product, inventory, cart, order, payment, conversation, assistant, behavior, Redis cache, and rate limiting.
- Python AI service with FastAPI, LangGraph, RAG, deterministic answer-quality tests, candidate-backed recommendation refs, and SSE output.
- React frontend covering AI shopping, product browsing, cart, and orders.
- MySQL/Flyway schema for product, inventory, users, conversations, cart, orders, payment, favorite, and behavior events.
- Redis integration for product/profile/recommendation cache and AI rate limiting.

The next work should not be "add more technology". It should close the gaps that currently make the system harder to trust, run, and explain.

## Development Order

```text
1. Align product fact source
2. Restore shared Java-Python contract governance
3. Add reproducible environment, configuration, and CI
4. Add AI service resilience and fallback
5. Add user profile/preference frontend
6. Expand real payment and after-sale boundaries
```

MQ is intentionally not in the first three stages. If it is added later, it should handle async side effects or long-running AI tasks, not replace transactional order/payment/inventory consistency.

## Stage 1: Align Product Fact Source

### Problem

Java has already moved recommendation candidates to SKU-level data and sends `spu_code`, `sku_code`, and `available_stock` to Python. However, Python's structured price and inventory lookup still reads `clothing_assistant/data/product_catalog.json`.

That means recommendation refs are mostly candidate-backed, but direct user questions such as "这件多少钱" or "黑色 L 码有货吗" can still answer from a second, stale product fact source.

### Goal

Production chat must answer price and inventory questions only from Java-provided candidates or Java-controlled internal APIs.

### Scope

- Add candidate-backed structured lookup in Python for price and inventory intents.
- Use Java-provided `candidates` first for product, color, size, price, and stock facts.
- Disable JSON product fact fallback for Java production requests.
- Keep JSON fallback only for local demo/test paths where Java candidates are absent and the mode is explicitly enabled.
- Update answer-quality tests so production cases prove price and inventory are candidate-backed.

### Main Files

- `AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/nodes.py`
- `AI-Clothing-Shopping-Assistant-System/clothing_assistant/tools/product_catalog.py`
- `AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/schemas.py`
- `AI-Clothing-Shopping-Assistant-System/tests/test_agent_pipeline.py`
- `AI-Clothing-Shopping-Assistant-System/tests/test_answer_quality_report.py`
- `AI-Clothing-Shopping-Assistant-System/tests/test_api.py`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonProductCandidate.java`
- `IntelligentOutfitRecommendationSystem/backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

### Acceptance Criteria

- Python price answers use `candidate.sale_price`, not `price_cny` from JSON.
- Python inventory answers use `candidate.available_stock` and `candidate.stock_status`.
- If no matching candidate exists, Python asks a follow-up or says the product cannot be confirmed from the current catalog.
- Python does not return a price, stock count, SKU, or product ref outside Java candidates in production request shape.
- Existing AI recommendation flow still works when Java passes valid candidates.

### Verification

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_api tests.test_agent_pipeline tests.test_recommendation_service tests.test_answer_quality_report -v
python -m compileall -q clothing_assistant tests
```

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw test -Dtest=AssistantServiceTests,RestPythonAssistantClientTests,PythonSseEventParserTests
```

### Not In This Stage

- Do not add MQ.
- Do not make Python directly write Java data.
- Do not migrate the full RAG knowledge store.

## Stage 2: Restore Shared Java-Python Contract Governance

### Problem

Both project `AGENTS.md` files require reading `../outfit-project-contract`, but that directory is missing in the current workspace. Contract docs also exist in multiple project-local locations, which increases the chance of Java and Python drifting.

### Goal

Make Java-Python API and SSE contract changes reviewable from one stable place.

### Scope

- Recreate or vendor a shared `outfit-project-contract` directory.
- Move the current assistant chat and streaming contract references into that shared directory.
- Keep project-local docs as references that point to the shared contract, not competing sources.
- Add a small contract checklist for every Java-Python change.
- Add contract tests or snapshots for Python request/response fields and SSE event payloads.

### Main Files

- `outfit-project-contract/AGENTS.md`
- `outfit-project-contract/docs/business-rules.md`
- `outfit-project-contract/docs/coding-boundary.md`
- `outfit-project-contract/docs/dev-checklist.md`
- `outfit-project-contract/contracts/assistant-streaming-chat/v1.md`
- `IntelligentOutfitRecommendationSystem/docs/contracts/CONTRACT_REF.md`
- `AI-Clothing-Shopping-Assistant-System/docs/contracts/python-ai-api-contract.md`
- Java assistant DTO tests under `IntelligentOutfitRecommendationSystem/backend/src/test/java/.../assistant/`
- Python API tests under `AI-Clothing-Shopping-Assistant-System/tests/test_api.py`

### Acceptance Criteria

- The shared contract path referenced by both `AGENTS.md` files exists.
- A developer can find the authoritative rules for product facts, user context, candidates, product refs, and SSE events in one place.
- Java and Python tests fail if required contract fields are renamed or removed.
- Project-local contract docs clearly point to the shared source instead of duplicating stale field lists.

### Verification

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw test -Dtest=AssistantControllerTests,AssistantServiceTests,RestPythonAssistantClientTests
```

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_api tests.test_chat_stream -v
```

### Not In This Stage

- Do not redesign the whole API.
- Do not introduce OpenAPI generation unless it stays small and directly protects the existing contract.

## Stage 3: Add Reproducible Environment, Configuration, And CI

### Problem

The current local setup is harder than it should be:

- `docker-compose.yml` maps MySQL as `3307:3306`.
- Java `application.properties` defaults to `localhost:3306`.
- No root `.env.example` was found.
- No Dockerfile or CI workflow was found in the current workspace.

### Goal

Make a fresh developer or interviewer able to run and verify the project predictably.

### Scope

- Add `.env.example` with non-secret local defaults.
- Align Java datasource defaults with Docker Compose or document a single override path.
- Add Dockerfiles for Java backend, Python AI service, and frontend if full-stack compose is desired.
- Extend compose to optionally run Java, Python, and frontend in addition to MySQL and Redis.
- Add CI workflow for Java unit tests, Python unit tests, frontend unit tests, and frontend build.
- Keep real credentials out of version control.

### Main Files

- `IntelligentOutfitRecommendationSystem/docker-compose.yml`
- `IntelligentOutfitRecommendationSystem/.env.example`
- `IntelligentOutfitRecommendationSystem/backend/Dockerfile`
- `AI-Clothing-Shopping-Assistant-System/Dockerfile`
- `IntelligentOutfitRecommendationSystem/frontend/Dockerfile`
- `.github/workflows/ci.yml`
- `IntelligentOutfitRecommendationSystem/README.md`
- `AI-Clothing-Shopping-Assistant-System/README.md`

### Acceptance Criteria

- Local dependency startup is documented with one command.
- The Java backend connects to the MySQL service using the documented port and credentials.
- Redis is available for cache/rate-limit flows.
- CI runs Java, Python, and frontend checks without requiring real model provider keys.
- Missing optional AI keys produce deterministic fallback behavior, not failing test setup.

### Verification

```bash
cd IntelligentOutfitRecommendationSystem
docker compose up -d mysql redis
```

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw verify
```

```bash
cd AI-Clothing-Shopping-Assistant-System
python -m unittest discover -v
python -m compileall -q clothing_assistant tests
```

```bash
cd IntelligentOutfitRecommendationSystem/frontend
npm test -- --run
npm run build
```

### Not In This Stage

- Do not deploy to cloud.
- Do not add Kubernetes.
- Do not require RabbitMQ just to run the local demo.

## Stage 4: Add AI Service Resilience And Fallback

### Problem

Java already has HTTP timeouts when calling Python, but the system does not yet have a clear resilience layer for repeated Python failures. AI should be an enhanced experience, not a dependency that breaks browsing, cart, order, or payment.

### Goal

When Python AI is slow or unavailable, Java returns controlled AI errors or fallback recommendations while the commerce flow remains usable.

### Scope

- Add an `AssistantFallbackService` in Java.
- Return a safe assistant message when Python `/chat` fails.
- For recommendation-like prompts, optionally return Java recommendation candidates with a clear fallback reason.
- Add a simple circuit-breaker abstraction around Python calls.
- Record request IDs and failure classes in logs without exposing raw provider errors to frontend.
- Add frontend display for controlled AI fallback state.

### Main Files

- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java`
- `IntelligentOutfitRecommendationSystem/backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- `IntelligentOutfitRecommendationSystem/frontend/src/features/assistant/ChatPanel.tsx`
- `IntelligentOutfitRecommendationSystem/frontend/src/shared/api/assistantStream.test.ts`

### Acceptance Criteria

- Python timeout or 500 does not leak stack traces, prompt text, provider details, API keys, or internal paths.
- Sync chat returns a controlled fallback response.
- Stream chat emits a controlled error event and closes cleanly.
- Product browse, cart, order, and mock payment still work while Python is down.
- Repeated Python failures trip a local circuit state or equivalent guard so Java does not keep hammering a dead service.

### Verification

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw test -Dtest=AssistantControllerTests,AssistantServiceTests,RestPythonAssistantClientTests,GlobalExceptionHandlerTests
```

```bash
cd IntelligentOutfitRecommendationSystem/frontend
npm test -- --run src/shared/api/assistantStream.test.ts src/features/assistant/assistantState.test.ts
```

### Not In This Stage

- Do not add distributed tracing infrastructure.
- Do not add Sentinel/Nacos/Gateway.
- Do not add MQ for synchronous chat.

## Stage 5: Add User Profile And Preference Frontend

### Problem

Java has profile, body data, and preference APIs, and the assistant context already reads those values. The frontend does not yet give users a first-class way to maintain them.

### Goal

Let users maintain the data that materially improves recommendations: gender, height, weight, preferred fit, styles, colors, disliked colors, preferred categories, and budget.

### Scope

- Add a "我的偏好" or "尺码偏好" frontend view.
- Load and update `/api/me/profile`, `/api/me/body-data`, and `/api/me/preferences`.
- Use compact forms with validation and clear save states.
- Refresh assistant context naturally on the next chat request.
- Keep sensitive auth/account changes out of this stage.

### Main Files

- `IntelligentOutfitRecommendationSystem/frontend/src/app/App.tsx`
- `IntelligentOutfitRecommendationSystem/frontend/src/pages/ProfilePreferencesPage.tsx`
- `IntelligentOutfitRecommendationSystem/frontend/src/shared/api/client.ts`
- `IntelligentOutfitRecommendationSystem/frontend/src/shared/api/types.ts`
- `IntelligentOutfitRecommendationSystem/frontend/src/styles.css`
- `IntelligentOutfitRecommendationSystem/backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/user/UserProfileControllerTests.java`
- `IntelligentOutfitRecommendationSystem/frontend/e2e/ai-shopping.spec.ts`

### Acceptance Criteria

- User can view and save profile basics.
- User can view and save body data used for sizing.
- User can view and save style/color/category/budget preferences.
- Saved preferences affect the next AI recommendation context through existing Java context assembly.
- UI handles loading, success, validation errors, and unauthenticated state.

### Verification

```bash
cd IntelligentOutfitRecommendationSystem/frontend
npm test -- --run
npm run build
```

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw test -Dtest=UserProfileControllerTests,UserProfileServiceTests
```

### Not In This Stage

- Do not build admin profile management.
- Do not add address book unless Stage 6 starts.
- Do not change the assistant contract unless required by missing profile fields.

## Stage 6: Expand Real Payment And After-Sale Boundaries

### Problem

Payment is currently suitable for demo and local testing. The public API has a strategy boundary and callback log, but only the `MOCK` strategy exists. Provider callbacks are recorded for audit and do not yet verify signatures or update payment state.

The project also lacks shipping address, shipment, refund, and after-sale flows.

### Goal

Make the commerce transaction boundary more realistic while keeping mock payment available for local development.

### Scope

- Add address book model and APIs if checkout needs a shipping destination.
- Add a real-payment adapter skeleton with signature verification rules, without requiring production credentials in CI.
- Turn verified provider callback into the same `confirmPaymentSuccess` path used by mock payment.
- Add refund/after-sale schema and state transitions.
- Keep payment, order, and inventory state changes inside Java-controlled transaction boundaries.
- Decide whether payment success side effects should remain best-effort sync or move to outbox/MQ after core payment correctness is tested.

### Main Files

- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/strategy/`
- `IntelligentOutfitRecommendationSystem/backend/src/main/resources/db/migration/`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/`
- `IntelligentOutfitRecommendationSystem/backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/`
- `IntelligentOutfitRecommendationSystem/frontend/src/pages/CartPage.tsx`
- `IntelligentOutfitRecommendationSystem/frontend/src/pages/OrdersPage.tsx`

### Acceptance Criteria

- Mock payment remains available and deterministic for demos.
- Unsupported payment channels are rejected before any order or inventory state changes.
- Verified callback can mark a pending payment successful through the same idempotent success path.
- Duplicate callbacks do not double-confirm stock or double-mark orders.
- Refund or after-sale state is explicit and does not corrupt historical order item snapshots.

### Verification

```bash
cd IntelligentOutfitRecommendationSystem/backend
./mvnw test -Dtest=PaymentServiceTests,PaymentControllerTests,PaymentMapperTests,OrderServiceTests,InventoryMapperTests
```

Run MySQL migration verification when the schema changes:

```bash
cd IntelligentOutfitRecommendationSystem/backend
RUN_MYSQL_TESTS=true ./mvnw test -Dtest=MySqlFlywayMigrationTests
```

### MQ Decision Point

Only consider MQ after verified payment success and behavior-event correctness are stable.

Good MQ candidates:

- `payment.succeeded` side effects such as behavior analytics and recommendation conversion.
- AI long tasks such as RAG rebuild, batch product tags, and evaluation reports.
- User profile refresh jobs derived from behavior events.

Bad first MQ candidates:

- Replacing order creation transaction.
- Replacing stock lock/confirm/release transaction.
- Replacing payment success idempotency.

## Tracking Checklist

- [x] Stage 1 complete: Product facts aligned to Java candidates/internal APIs.
- [x] Stage 2 complete: Shared contract governance restored.
- [x] Stage 3 complete: Reproducible environment and CI added.
- [x] Stage 4 complete: AI resilience and fallback added.
- [x] Stage 5 complete: User profile/preference frontend added.
- [x] Stage 6 complete: Real payment and after-sale boundaries expanded.

## Recommended Next Step

六阶段路线已经完成，下一步进入发布前收尾和联调清理。

请从 [发布前收尾交接文档](./2026-07-05-release-readiness-handoff.md) 继续。下一步应在下面几项中选择：

- 按文档里的阶段分组整理本地提交。
- 推送当前分支并创建 Pull Request。
- 使用非生产密钥执行真实支付沙箱回调联调。
- 如果下一个产品目标是用户可见的退款/售后流程，则补前端售后入口。
