# Java Module Boundaries Phase B Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give Assistant explicit catalog-candidate and conversation application boundaries without adding interface/implementation pairs or changing HTTP contracts.

**Architecture:** Extract the complete recommendation-candidate cache/hydration workflow from `ProductCatalogService` into one concrete `RecommendationCandidateQueryService`. Promote the existing conversation use-case service to `ConversationApplicationService`, replace its public `ChatSession` return with an ownership assertion, and update Assistant to depend only on these application boundaries.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis, Redis Cache-Aside, JUnit 5, Mockito, ArchUnit, Maven.

---

### Task 1: Add failing Assistant boundary rules

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java`

- [x] Add rules prohibiting Assistant from depending on `ProductCatalogService`, `ConversationService`, conversation Mapper, or conversation Model packages.
- [x] Run `cd backend; .\mvnw.cmd -Dtest=ModuleArchitectureTests test` and confirm RED on the current `AssistantContextService` and `AssistantService` dependencies.

### Task 2: Extract the Catalog Candidate Query seam

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/RecommendationCandidateQueryService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductController.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: product and assistant tests.

- [x] Move normalization, static snapshot caching, live-fact hydration, availability filtering, deterministic ordering, and candidate cache-key generation together into the new service.
- [x] Keep search/detail/SKU behavior in `ProductCatalogService`; do not leave a delegating candidate method.
- [x] Update both product controllers and Assistant context assembly to use the candidate service.
- [x] Run product, controller, and Assistant context tests.

### Task 3: Establish the Conversation Application Service

**Files:**
- Rename: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationService.java`
- To: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationApplicationService.java`
- Modify: conversation controllers, Assistant services, and their tests.

- [x] Preserve create/list/history/archive/append behavior in the renamed application service.
- [x] Replace the public `requireConversation(...): ChatSession` method with `assertOwned(...): void`; retain a private session lookup for internal operations.
- [x] Update Assistant to call only `createConversation`, `assertOwned`, `getMessages`, and `appendMessage` on this boundary.
- [x] Run conversation and Assistant tests.

### Task 4: Verify and record Phase B

**Files:**
- Modify: `docs/architecture/java-module-boundaries.md`
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [x] Run focused catalog, conversation, Assistant, and architecture tests.
- [x] Run `cd backend; .\mvnw.cmd verify`.
- [x] Record exact totals and remaining OrderMapper exceptions; do not claim week two complete.
- [x] Run `git diff --check` and inspect the final diff.

## Plan Self-Review

- Both seams own real policy; neither is a one-method interface over one implementation.
- No API schema, table, migration, or Java-Python contract changes.
- Existing Redis static/dynamic fact behavior moves intact rather than being duplicated.
- Payment/AfterSale OrderMapper exceptions remain the next phase.
- No commit is created because the user did not request one.
