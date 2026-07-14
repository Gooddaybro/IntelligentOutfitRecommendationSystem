# Lock Concurrency Trace Demo Implementation Plan

> **For agentic workers:** This plan is executed inline in the current learning session. The learner owns the three target business flows; the assistant owns deterministic concurrency plumbing and verification.

**Goal:** Create a deterministic Java learning demo that lets the learner observe an unsafe stock update, a pessimistic lock, and an optimistic versioned update.

**Architecture:** One class in the existing `com.recommendation.learning` package owns a small in-memory inventory and three scenario runners. `CyclicBarrier`, `CountDownLatch`, and `ReentrantLock` make the important interleavings reproducible; a synchronized compare-and-set helper models the database's atomic conditional update without pretending to be a database.

**Tech Stack:** Java 21, JDK `java.util.concurrent`, Maven/JUnit 5 already present in the backend.

---

### Task 1: Add the deterministic learning harness

**Files:**
- Create: `backend/src/main/java/com/recommendation/learning/LockConcurrencyTraceDemo.java`
- Test: `backend/src/test/java/com/recommendation/learning/LockConcurrencyTraceDemoTest.java`

- [ ] Define `ScenarioResult`, `Inventory`, `Snapshot`, trace collection, and executor/latch helpers. Keep these helpers free of the target business decisions.
- [ ] Add public scenario runners `runWithoutLock`, `runWithPessimisticLock`, and `runWithOptimisticLock`, plus a `main` that prints each result.
- [ ] Mark only `buyWithoutLock`, `buyWithPessimisticLock`, and `buyWithOptimisticLock` as learner-owned implementation boundaries. The methods must initially throw `UnsupportedOperationException` so the first test run demonstrates the missing logic.

### Task 2: Write and run the failing behavior tests

**Files:**
- Create: `backend/src/test/java/com/recommendation/learning/LockConcurrencyTraceDemoTest.java`

- [ ] Assert the no-lock scenario has two successes and final stock `0`, proving the two requests both passed the stale check.
- [ ] Assert the pessimistic scenario has one success, one failure, final stock `0`, and a trace entry showing T2 waited.
- [ ] Assert the optimistic scenario has one success, one failure, final stock `0`, final version `1`, and a trace entry showing a version conflict.
- [ ] Run `cd backend; .\mvnw.cmd -Dtest=LockConcurrencyTraceDemoTest test` and record the expected failure from the three learner-owned methods.

### Task 3: Implement the learner-owned core flow

**Files:**
- Modify: `backend/src/main/java/com/recommendation/learning/LockConcurrencyTraceDemo.java`

- [ ] Implement the no-lock flow as read, wait at the supplied read barrier, check the observed stock, and write the derived stock without synchronization.
- [ ] Implement the pessimistic flow as `lock.lock()`, read/check/decrement inside `try`, and `unlock()` in `finally`; keep the supplied hooks so the trace shows T2 waiting.
- [ ] Implement the optimistic flow as snapshot, barrier, stock check, atomic compare-and-set with the observed version, one bounded re-read/retry, and failure when the new snapshot has no stock.

### Task 4: Verify and run the learner demo

**Files:**
- No additional files.

- [ ] Re-run the targeted JUnit command and confirm all three behaviors pass.
- [ ] Compile/run the main class and compare the printed trace with the learner's prediction before execution.
- [ ] Run `cd backend; .\mvnw.cmd verify` only after the targeted test is green; report any pre-existing unrelated failures separately.

