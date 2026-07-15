# Java Module Boundaries Phase A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Start week-two modular-monolith governance with an executable dependency map and an Inventory application boundary that removes Order and Payment access to `InventoryMapper`.

**Architecture:** Keep the existing single Maven module and top-level business packages. Add test-only ArchUnit rules instead of moving packages, then introduce one concrete `InventoryApplicationService`—not an interface/implementation pair—to own stock lock, confirm, and release semantics. Existing Order and Payment transactions call that application service while Spring transaction propagation keeps the operations in the caller's transaction.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis, JUnit 5, Mockito, ArchUnit 1.4.x, Maven.

---

### Task 1: Record the current module dependency map

**Files:**
- Create: `docs/architecture/java-module-boundaries.md`

- [x] Document the seven logical groups: identity, catalog, trade-core, engagement, assistant, platform, and application bootstrap.
- [x] Record current cross-module edges and the two known temporary cross-Mapper exceptions: `PaymentService -> OrderMapper` and `AfterSaleService -> OrderMapper`.
- [x] Record table ownership and state that Mapper packages are module-private implementation details.

### Task 2: Add executable architecture rules

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java`

- [x] Add `com.tngtech.archunit:archunit-junit5` as a test dependency.
- [x] Add rules proving controllers do not access Mappers, `common` does not depend on business packages, top-level modules are cycle-free, and only the two documented temporary exceptions may access another module's Mapper.
- [x] Run `cd backend; .\mvnw.cmd -Dtest=ModuleArchitectureTests test` and confirm RED because Order and Payment still access `InventoryMapper` and the test intentionally does not exempt those dependencies.

### Task 3: Introduce the Inventory application boundary with TDD

**Files:**
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryApplicationServiceTests.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service/InventoryApplicationService.java`

- [x] Write tests for positive argument validation, successful lock/confirm/release, and mapper result `0` conversion to the current business exceptions.
- [x] Run the focused test and confirm compilation fails because `InventoryApplicationService` does not exist.
- [x] Implement one concrete service with `lock`, `confirm`, and `release`; keep transaction ownership in Order/Payment and do not add a second abstraction layer.
- [x] Re-run the focused tests and confirm GREEN.

### Task 4: Refactor Order and Payment callers

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/order/service/OrderService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/service/PaymentService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/order/OrderServiceTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/payment/PaymentServiceTests.java`

- [x] Replace direct `InventoryMapper` dependencies with `InventoryApplicationService`.
- [x] Preserve current exception text and transaction behavior.
- [x] Update unit-test mocks and interaction assertions to target the application service.
- [x] Run Order, Payment, Inventory, and architecture tests; confirm they pass.

### Task 5: Verify and record Phase A status

**Files:**
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [x] Record the new architecture gate and Inventory seam as Phase A, without claiming the full second week is complete.
- [x] Run `cd backend; .\mvnw.cmd verify`.
- [x] Record exact test totals, skips, Checkstyle output, and Docker/Testcontainers gaps.
- [x] Run `git diff --check` and inspect the final scoped diff.

## Plan Self-Review

- This phase covers the first dependency map, executable gate, and one approved high-value seam.
- Catalog Candidate Query, Conversation Application Service, and the two OrderMapper exceptions remain explicit later tasks, not hidden placeholders in this phase.
- The design adds one concrete service rather than a speculative interface plus implementation.
- No runtime dependency is added; ArchUnit is test-only.
- No commit is created because the user did not request one.
