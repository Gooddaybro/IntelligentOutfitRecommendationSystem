# Trace 与熔断可观测性 Phase C Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Java 到 Python 的同步与 SSE 请求可通过 request ID/traceparent 关联，并可观察 AI 熔断器当前状态和恢复迁移。

**Architecture:** 在现有 HTTP 拦截器中验证或生成 W3C traceparent，并放入 MDC；现有 SSE TaskDecorator 复制整份 MDC；原生 JDK HttpClient 从 MDC 写出 request ID 和 traceparent。熔断器配置直接使用 Micrometer 注册一个数值状态 Gauge 和固定状态迁移 Counter，不引入额外 tracing 或 Resilience4j Spring 适配依赖。

**Tech Stack:** Java 21、Spring MVC、JDK HttpClient、SLF4J MDC、Micrometer、Resilience4j、JUnit 5、AssertJ

---

### Task 1: Trace context 入口和异步传播

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptor.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptorTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantStreamingConfigTests.java`

- [x] 先测试合法 traceparent 被接收、非法或缺失值被替换，并写回响应头。
- [x] 运行测试，确认因 traceparent 尚未进入 MDC/响应而失败。
- [x] 实现固定 W3C v00 格式校验和 SecureRandom 生成，完成请求后清理 MDC。
- [x] 验证 SSE 执行器复制 requestId 和 traceparent，且不会污染下一任务。

### Task 2: Java 到 Python Header 传播

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestPythonAssistantClientTests.java`

- [x] 先测试同步与 SSE 请求都携带 `X-Request-Id` 和 `traceparent`。
- [x] 运行测试，确认因 Header 缺失而失败。
- [x] 复用统一请求构建 helper，从 MDC 写出已校验的两个关联 Header。
- [x] 运行客户端聚焦测试并确认通过。

### Task 3: 熔断器状态和迁移指标

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantResilienceConfig.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantResilienceConfigTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`

- [x] 先测试 `app.ai.circuit.state` 初始为 0、打开为 1，并记录固定 from/to 状态迁移。
- [x] 运行测试，确认指标尚不存在而失败。
- [x] 注册单个数值 Gauge，并由 ApplicationMetrics 记录低基数迁移 Counter。
- [x] 运行指标和熔断配置聚焦测试并确认通过。

### Task 4: 文档和完整验证

**Files:**
- Create: `docs/runbooks/ai-assistant-dependency-failure.md`
- Modify: `docs/architecture/observability.md`
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [x] 写明告警含义、首查项、降级/恢复、交易影响和人工介入条件。
- [x] 更新指标目录、trace Header 规则和第三周 Phase C 状态。
- [x] 运行聚焦测试。
- [x] 运行 `backend\\mvnw.cmd verify`、`git diff --check` 并记录证据。
