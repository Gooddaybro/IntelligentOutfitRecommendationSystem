package com.recommendation.learning;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 用两个可控线程展示库存更新中的无锁、悲观锁和乐观锁边界。
 *
 * <p>本类只提供确定性并发编排和状态适配器；三个 buy 方法是学习者需要重建的业务核心。
 * 适配器模拟数据库的状态读写，但不替代真实事务或数据库锁。</p>
 */
public final class LockConcurrencyTraceDemo {

    private static final int INITIAL_STOCK = 1;

    private static final long COORDINATION_TIMEOUT_SECONDS = 3;

    private LockConcurrencyTraceDemo() {
    }

    /**
     * 运行三个场景并打印重要的线程轨迹。
     *
     * @param args ignored
     * @throws Exception when a worker cannot complete
     */
    public static void main(String[] args) throws Exception {
        List<ScenarioResult> results = List.of(
                runWithoutLock(),
                runWithPessimisticLock(),
                runWithOptimisticLock());
        for (ScenarioResult result : results) {
            result.print();
        }
    }

    /**
     * 运行无锁场景；两个 worker 被安排为先读后写。
     *
     * @return scenario result
     * @throws Exception when a worker cannot complete
     */
    public static ScenarioResult runWithoutLock() throws Exception {
        Inventory inventory = new Inventory(INITIAL_STOCK);
        Trace trace = new Trace();
        CyclicBarrier afterRead = new CyclicBarrier(2,
                () -> trace.add("both workers finished reading"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = List.of(
                    executor.submit(() -> buyWithoutLock("T1", inventory, afterRead, trace)),
                    executor.submit(() -> buyWithoutLock("T2", inventory, afterRead, trace)));
            return collectResult("without-lock", inventory, trace, futures);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 运行悲观锁场景，并确定性地让 T2 在 T1 的临界区外等待。
     *
     * @return scenario result
     * @throws Exception when a worker cannot complete
     */
    public static ScenarioResult runWithPessimisticLock() throws Exception {
        Inventory inventory = new Inventory(INITIAL_STOCK);
        Trace trace = new Trace();
        PessimisticHooks hooks = new PessimisticHooks(trace);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(
                    () -> buyWithPessimisticLock("T1", inventory, hooks, trace));
            awaitLatch(hooks.firstAcquired(), "T1 did not acquire pessimistic lock");
            Future<Boolean> second = executor.submit(
                    () -> buyWithPessimisticLock("T2", inventory, hooks, trace));
            awaitLatch(hooks.secondAttempted(), "T2 did not attempt pessimistic lock");
            trace.add("T2 is waiting for T1 to commit");
            hooks.releaseFirst().countDown();
            return collectResult("pessimistic-lock", inventory, trace, List.of(first, second));
        } finally {
            hooks.releaseFirst().countDown();
            executor.shutdownNow();
        }
    }

    /**
     * 运行带版本号的乐观锁场景；两个 worker 先读取同一快照。
     *
     * @return scenario result
     * @throws Exception when a worker cannot complete
     */
    public static ScenarioResult runWithOptimisticLock() throws Exception {
        Inventory inventory = new Inventory(INITIAL_STOCK);
        Trace trace = new Trace();
        CyclicBarrier afterSnapshot = new CyclicBarrier(2,
                () -> trace.add("both workers captured version 0"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = List.of(
                    executor.submit(() -> buyWithOptimisticLock("T1", inventory, afterSnapshot, trace)),
                    executor.submit(() -> buyWithOptimisticLock("T2", inventory, afterSnapshot, trace)));
            return collectResult("optimistic-lock", inventory, trace, futures);
        } finally {
            executor.shutdownNow();
        }
    }

    // LEARNER_WORK_START: rebuild the read/check/write order for the no-lock scenario.
    private static boolean buyWithoutLock(
            String worker,
            Inventory inventory,
            CyclicBarrier afterRead,
            Trace trace) throws Exception {
        // worker：当前执行购买的线程名称，例如 T1 或 T2，用来输出轨迹。
        // inventory：库存对象；本方法故意不加锁，专门演示并发问题。
        // afterRead：两个线程都读完库存后，才能继续往下执行的“屏障”。
        // trace：记录每一步发生了什么，方便你观察执行顺序。
        // throws Exception：afterRead.await(...) 将来可能抛出等待相关异常。

        // 下面这一行只是占位符，表示“核心逻辑还没有写完”。
        // 你实现方法时，需要删除它，换成自己的读取、判断和写回流程。
        //  throw new UnsupportedOperationException("learner implementation required");
        int curinven = inventory.stock;
        trace.add(worker + "当前库存:" + curinven);
        afterRead.await(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (curinven <= 0) {
            trace.add(worker + "读取失败");
            return false;
        }
        inventory.writeStock(curinven - 1);
        trace.add(worker + "购买成功:" + (curinven - 1));
        return true;
    }
    // LEARNER_WORK_END

    // LEARNER_WORK_START: define the lock boundary and failure path for the pessimistic scenario.
    private static boolean buyWithPessimisticLock(
            String worker,
            Inventory inventory,
            PessimisticHooks hooks,
            Trace trace) throws Exception {

        hooks.beforeLock(worker);
        hooks.acquireLock();
        try {
            hooks.afterLock(worker);
            int curinven = inventory.readStock();
            trace.add(worker+"当前库存："+curinven);
            if (curinven <= 0) {
                return false;
            }
            inventory.writeStock(curinven - 1);
            return true;
        }finally {
            hooks.releaseLock();
        }

    }
    // LEARNER_WORK_END

    // LEARNER_WORK_START: implement version check, bounded retry, and no-stock failure.
    private static boolean buyWithOptimisticLock(
            String worker,
            Inventory inventory,
            CyclicBarrier afterSnapshot,
            Trace trace) throws Exception {
        Snapshot snapshot = inventory.snapshot();
        trace.add(snapshot.stock()+":"+ snapshot.version());
        afterSnapshot.await(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if(snapshot.stock() <= 0) {
            trace.add(worker+"库存没有");
            return false;
        }
        if(inventory.tryDecrease(snapshot)){
            trace.add(worker+"成功了");
            return true;
        }
        trace.add(worker + " version conflict, retry once");
        Snapshot snapshot1 = inventory.snapshot();
        if(snapshot1.stock() <= 0) {
            return false;
        }
        if(inventory.tryDecrease(snapshot1)) {
            return true;
        }
        return false;
    }
    // LEARNER_WORK_END

    private static ScenarioResult collectResult(
            String name,
            Inventory inventory,
            Trace trace,
            List<Future<Boolean>> futures) throws Exception {
        int successes = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successes++;
            }
        }
        return new ScenarioResult(
                name,
                successes,
                futures.size() - successes,
                inventory.stock(),
                inventory.version(),
                trace.snapshot());
    }

    private static void awaitLatch(CountDownLatch latch, String message) throws Exception {
        if (!latch.await(COORDINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * Result and trace of one deterministic scenario.
     */
    public record ScenarioResult(
            String name,
            int successes,
            int failures,
            int finalStock,
            int finalVersion,
            List<String> trace) {

        private void print() {
            System.out.printf(
                    "%n[%s] success=%d, failure=%d, stock=%d, version=%d%n",
                    name, successes, failures, finalStock, finalVersion);
            trace.forEach(line -> System.out.println("  " + line));
        }
    }

    private record Snapshot(int stock, int version) {
    }

    private static final class Inventory {

        private final AtomicInteger versionCounter = new AtomicInteger();

        private int stock;

        private Inventory(int stock) {
            this.stock = stock;
        }

        private int readStock() {
            return stock;
        }

        private void writeStock(int newStock) {
            stock = newStock;
        }

        private Snapshot snapshot() {
            return new Snapshot(stock, versionCounter.get());
        }

        private synchronized boolean tryDecrease(Snapshot expected) {
            if (stock != expected.stock() || versionCounter.get() != expected.version()) {
                return false;
            }
            stock--;
            versionCounter.incrementAndGet();
            return true;
        }

        private synchronized int stock() {
            return stock;
        }

        private int version() {
            return versionCounter.get();
        }
    }

    private static final class Trace {

        private final List<String> entries = new CopyOnWriteArrayList<>();

        private void add(String entry) {
            entries.add(entry);
        }

        private List<String> snapshot() {
            return List.copyOf(entries);
        }
    }

    private static final class PessimisticHooks {

        private final Trace trace;

        // 两个 worker 必须共享同一把锁，否则每个线程各锁自己的对象，无法互斥。
        private final ReentrantLock lock = new ReentrantLock();

        private final CountDownLatch firstAcquired = new CountDownLatch(1);

        private final CountDownLatch secondAttempted = new CountDownLatch(1);

        private final CountDownLatch releaseFirst = new CountDownLatch(1);

        private PessimisticHooks(Trace trace) {
            this.trace = trace;
        }

        private void beforeLock(String worker) {
            trace.add(worker + " attempts lock");
            if ("T2".equals(worker)) {
                secondAttempted.countDown();
            }
        }

        private void acquireLock() {
            lock.lock();
        }

        private void releaseLock() {
            lock.unlock();
        }

        private void afterLock(String worker) throws InterruptedException {
            if ("T1".equals(worker)) {
                trace.add("T1 acquired lock");
                firstAcquired.countDown();
                releaseFirst.await();
            } else {
                trace.add("T2 acquired lock after T1 released it");
            }
        }

        private CountDownLatch firstAcquired() {
            return firstAcquired;
        }

        private CountDownLatch secondAttempted() {
            return secondAttempted;
        }

        private CountDownLatch releaseFirst() {
            return releaseFirst;
        }
    }
}
