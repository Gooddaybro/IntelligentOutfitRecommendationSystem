# Lock Concurrency Trace Demo 设计

## 目标

在不依赖 MySQL 的前提下，用两个可控的 Java 线程稳定复现库存超卖，并对比无锁、悲观锁和乐观锁的执行轨迹。这个 Demo 只用于学习，不改变生产库存代码。

## 项目边界

- 生产库存路径保持不动；当前 `OrderService` 使用事务，`InventoryMapper.lockStock` 使用 `available_stock >= quantity` 条件更新。
- 现有未跟踪的 `backend/src/main/java/com/recommendation/learning/OversellingLockDemo.java` 保留，不覆盖、不重命名。
- 新 Demo 放在 `com.recommendation.learning` 包下，只使用 JDK 并发工具。

## 设计

### 三个场景

1. **无锁**：两个线程在屏障处都读到 `stock=1`，随后分别写入 `stock=0`，两个请求都返回成功，形成超卖。
2. **悲观锁**：使用 `ReentrantLock` 包住“读取、判断、扣减”临界区。测试编排让 T1 持锁时 T2 到达锁，T2 必须等待；T1 提交后 T2 读到 `stock=0` 并失败。
3. **乐观锁**：两个线程先读同一个 `(stock=1, version=0)` 快照，再通过原子 compare-and-set 更新。只有一个线程能把版本更新为 `1`；另一个线程记录冲突，重新读取一次，发现库存为 `0` 后失败。

### 文件职责

- `LockConcurrencyTraceDemo.java`：库存状态、并发编排、轨迹输出和三个 learner-owned 核心方法。
- `LockConcurrencyTraceDemoTest.java`：验证三个场景的成功数、最终库存、版本号和关键轨迹。

### 学习边界

助手提供线程池、屏障、锁、CAS 辅助器、结果汇总和测试；学习者填写三个核心流程中的读取顺序、库存判断、锁边界、条件更新和有限重试。辅助器不模拟数据库事务，因此 Demo 之后仍需把模型映射到 `@Transactional`、`SELECT ... FOR UPDATE` 和 SQL 条件更新。

## 验证

先运行针对性测试确认 learner-owned 方法尚未完成，再由学习者实现后重新运行：

```powershell
cd backend
.\mvnw.cmd -Dtest=LockConcurrencyTraceDemoTest test
```

运行主程序查看时间线：

```powershell
cd backend
java -cp target/classes com.recommendation.learning.LockConcurrencyTraceDemo
```

