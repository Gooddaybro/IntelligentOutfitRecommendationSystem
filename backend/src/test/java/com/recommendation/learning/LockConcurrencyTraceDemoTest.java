package com.recommendation.learning;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockConcurrencyTraceDemoTest {

    @Test
    void withoutLockShowsTwoSuccessesAgainstOneStock() throws Exception {
        LockConcurrencyTraceDemo.ScenarioResult result =
                LockConcurrencyTraceDemo.runWithoutLock();

        assertEquals(2, result.successes());
        assertEquals(0, result.finalStock());
        assertTrue(result.trace().contains("both workers finished reading"));
    }

    @Test
    void pessimisticLockLetsOnlyOneWorkerBuy() throws Exception {
        LockConcurrencyTraceDemo.ScenarioResult result =
                LockConcurrencyTraceDemo.runWithPessimisticLock();

        assertEquals(1, result.successes());
        assertEquals(1, result.failures());
        assertEquals(0, result.finalStock());
        assertTrue(result.trace().contains("T2 is waiting for T1 to commit"));
    }

    @Test
    void optimisticLockRejectsStaleVersionAndDoesOneBoundedRetry() throws Exception {
        LockConcurrencyTraceDemo.ScenarioResult result =
                LockConcurrencyTraceDemo.runWithOptimisticLock();

        assertEquals(1, result.successes());
        assertEquals(1, result.failures());
        assertEquals(0, result.finalStock());
        assertEquals(1, result.finalVersion());
        assertTrue(result.trace().stream()
                .anyMatch(entry -> entry.contains("version conflict")));
    }
}
