# AI Service Resilience And Fallback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep Java commerce flows usable when the Python AI service fails, times out, or repeatedly returns invalid responses.

**Architecture:** Add an `AssistantFallbackService` as a deep module behind a small interface. `AssistantService` remains the orchestration point: it saves the user message, builds Java-owned context, tries Python when allowed, records success/failure into the fallback module, and returns a controlled fallback response or stream error without exposing provider internals.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, existing assistant DTOs.

## Global Constraints

- Java remains the source of truth for product, price, inventory, order, payment, user, and session facts.
- Python failures must not leak stack traces, internal paths, prompt text, provider details, or API keys.
- Do not add MQ, Sentinel, Nacos, Gateway, or distributed tracing in Stage 4.
- Do not change Java-Python field contracts unless the shared manifest and both contract tests are updated.

---

## Files

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Optional verify: `frontend/src/shared/api/assistantStream.test.ts`

## Task 1: Synchronous Fallback

**Interfaces:**
- Consumes: `AssistantChatRequest`, `AssistantContext`, failure information
- Produces: safe `PythonChatResponse` with empty `product_refs`

- [x] **Step 1: Write failing service test**

Expected behavior:

- Python client throws `ExternalServiceException`.
- Java returns `AssistantChatResponse` instead of throwing.
- Answer is a safe fallback message.
- No product refs are returned.
- User message and fallback assistant message are both persisted.
- Sensitive failure text is not included in the response.

- [x] **Step 2: Implement fallback service and sync catch path**

`AssistantFallbackService` owns safe fallback copy and consecutive failure counting. `AssistantService.chat` catches runtime failures from Python, records the failure, and uses fallback output.

## Task 2: Local Circuit Guard

**Interfaces:**
- Produces: `shouldBypassPython()` based on consecutive failure threshold

- [x] **Step 1: Write failing repeated-failure test**

Expected behavior:

- After threshold consecutive Python failures, the next sync request skips the Python client.
- The skipped request still returns fallback and persists the assistant fallback message.

- [x] **Step 2: Implement guard**

Default threshold is 3 consecutive failures and can be overridden by `app.ai.fallback.failure-threshold`.

## Task 3: Streaming Fallback

**Interfaces:**
- Produces: controlled SSE `error` event and clean stream completion

- [x] **Step 1: Write failing stream guard test**

Expected behavior:

- When the guard is open, `streamChat` emits a safe stream error and does not call Python.
- It does not persist a fake assistant success message.

- [x] **Step 2: Implement stream guard path**

Before submitting a Python stream task, `AssistantService.streamChat` checks the fallback guard and returns a controlled error event if Python is currently bypassed.

## Task 4: Verification

- [x] **Step 1: Run focused Java tests**

```bash
cd backend
sh ./mvnw -q -Dtest=AssistantServiceTests,AssistantControllerTests,RestPythonAssistantClientTests,GlobalExceptionHandlerTests test
```

- [x] **Step 2: Run frontend assistant stream tests**

```bash
cd frontend
npm test -- --run src/shared/api/assistantStream.test.ts src/features/assistant/assistantState.test.ts
```

- [x] **Step 3: Run root local check**

```bash
cd ../..
sh scripts/check-local.sh
```

## Completion Criteria

- Sync chat returns a controlled fallback response when Python fails.
- Stream chat emits a controlled error event when Python fails or the local guard is open.
- Repeated Python failures stop hammering the Python service until the process is restarted or the guard is reset by a successful call.
- No fallback response contains provider details, stack traces, API keys, internal paths, or prompt text.
- Commerce flows remain Java-owned and no MQ is introduced.
