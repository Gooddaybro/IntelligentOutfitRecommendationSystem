# Learning Demo Isolation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove Java learning demos from the production artifact and Checkstyle gate while preserving their runnable learning tests.

**Architecture:** Reuse Maven's existing test source set instead of creating another module. Move the complete `com.recommendation.learning` package from `src/main/java` to the matching `src/test/java` package; Maven will compile it only for tests, so the demos remain runnable from `target/test-classes` but cannot enter the production JAR.

**Tech Stack:** Java 21, Maven, JUnit 5, existing Spring test classpath.

---

### Task 1: Record the failing production-quality baseline

**Files:**
- Inspect: `backend/src/main/java/com/recommendation/learning/*.java`
- Inspect: `backend/checkstyle.xml`

- [x] Run `cd backend; .\mvnw.cmd checkstyle:check`.
- [x] Confirm the failures come from learning files under the production source set.

### Task 2: Move learning code to the test source set

**Files:**
- Move: `backend/src/main/java/com/recommendation/learning/*.java`
- To: `backend/src/test/java/com/recommendation/learning/*.java`

- [x] Move all six learning classes without changing their package names or learner-owned content.
- [x] Keep `LockConcurrencyTraceDemoTest.java` beside the moved demo.
- [x] Add the planned assertion that the optimistic trace contains one version-conflict event.
- [x] Run `cd backend; .\mvnw.cmd -Dtest=LockConcurrencyTraceDemoTest test` and confirm three tests pass.

### Task 3: Update the learning command and verify both boundaries

**Files:**
- Modify: `docs/superpowers/specs/2026-07-14-lock-concurrency-demo-design.md`
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [x] Change the demo runtime classpath from `target/classes` to `target/test-classes` after `test-compile`.
- [x] Record that learning demos are test-only and no longer part of the production artifact.
- [x] Run `cd backend; .\mvnw.cmd verify`.
- [x] Run the demo with `cd backend; .\mvnw.cmd -q test-compile; java -cp target/test-classes com.recommendation.learning.LockConcurrencyTraceDemo`.
- [x] Report real Docker/Testcontainers skips separately; do not equate H2 or skipped container tests with real MySQL/Redis verification.

## Plan Self-Review

- The plan implements the already-approved first-week rule without adding a Maven module or dependency.
- All learner files remain present and runnable; only their build scope changes.
- No production API, database schema, or Java-Python contract changes.
- No commit is created because the user did not request one.
