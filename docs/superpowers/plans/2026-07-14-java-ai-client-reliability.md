# Java AI Client Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Java 调用 Python AI 的同步与 SSE 通道具备内部认证、独立超时、可自动恢复的熔断和跨线程日志上下文，同时保持 Java 现有安全降级响应。

**Architecture:** 保留 `RestPythonAssistantClient` 作为纯 HTTP Adapter，在其外增加一个实现相同 Interface 的 Resilience4j Core 装饰 Adapter。由于 Resilience4j 的 Spring Boot 4 Starter 兼容性尚未作为本项目基线验证，本计划只引入 `resilience4j-circuitbreaker` Core 并手工创建单个 CircuitBreaker；`AssistantFallbackService` 退化为无状态安全文案模块，熔断状态完全由装饰 Adapter 管理。

**Tech Stack:** Java 21, Spring Boot 4.0.6, JDK `HttpClient`, Resilience4j Core 2.4.0, JUnit 5, AssertJ, Mockito, Maven Checkstyle

---

## Scope

本计划只修改 Java 后端，不修改 Python/LangGraph 运行时代码。

包括：

- Java→Python `X-Internal-Token`；
- 同步与 SSE 独立超时；
- Resilience4j CircuitBreaker 的 CLOSED/OPEN/HALF_OPEN 恢复；
- 同步和 SSE 共用同一个 Python 依赖熔断器；
- `AssistantFallbackService` 移除自制计数状态；
- SSE 执行器传播 MDC；
- 聚焦测试和 Maven Verify。

不包括：

- Python 端 Token 校验实现；
- Redis、订单幂等、ArchUnit、Actuator、RabbitMQ；
- LLM 请求重试；
- SSE 协议升级；
- 微服务拆分。

## File Map

### Create

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantResilienceConfig.java`：创建 Python 专属 CircuitBreaker。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClient.java`：同步和 SSE 的熔断装饰 Adapter。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClientTests.java`：验证 OPEN、HALF_OPEN、恢复、SSE 终态。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantStreamingConfigTests.java`：验证 MDC 跨线程传播和清理。

### Modify

- `backend/pom.xml`：增加 Resilience4j Core 版本和依赖。
- `backend/src/main/resources/application.properties`：替换旧失败计数配置，增加 CircuitBreaker 与内部 Token Client 配置。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`：发送内部 Token，使用独立同步/SSE 超时。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java`：删除可变失败计数，只保留安全响应构造。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`：删除手工熔断状态读写，统一捕获装饰 Client 的失败并降级。
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantStreamingConfig.java`：增加 MDC TaskDecorator。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestPythonAssistantClientTests.java`：验证 Header 和两个 timeout 构造参数。
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`：改为验证无状态 Fallback 和 CircuitBreaker 拒绝后的业务降级。

## Task 1: Harden the HTTP Adapter Contract

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestPythonAssistantClientTests.java`
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: Write failing tests for internal authentication and separate stream timeout**

在 `RestPythonAssistantClientTests` 的同步测试中记录内部 Header：

```java
AtomicReference<String> internalTokenHeader = new AtomicReference<>();
```

在现有 `/chat` Context Lambda 的第一行插入：

```java
internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
```

将 Client 构造改为计划中的五参数形式，并增加断言：

```java
RestPythonAssistantClient client = new RestPythonAssistantClient(
        baseUrl,
        1000,
        5000,
        9000,
        "test-python-token"
);

assertThat(internalTokenHeader.get()).isEqualTo("test-python-token");
```

在流式测试中同样记录 Header：

```java
AtomicReference<String> internalTokenHeader = new AtomicReference<>();
```

在现有 `/chat/stream` Context Lambda 的第一行插入：

```java
internalTokenHeader.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));

assertThat(internalTokenHeader.get()).isEqualTo("test-python-token");
```

新增独立 Stream Timeout 测试。服务端延迟 250ms，Client 的同步超时保持 5 秒、Stream 超时设置为 50ms：

```java
@Test
void usesIndependentTimeoutForStreamRequests() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/chat/stream", exchange -> {
        try {
            Thread.sleep(250L);
            exchange.sendResponseHeaders(204, -1);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    });
    server.start();
    String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    RestPythonAssistantClient client = new RestPythonAssistantClient(
            baseUrl,
            1000,
            5000,
            50,
            "test-python-token"
    );
    CapturingStreamHandler handler = new CapturingStreamHandler();

    client.streamChat(minimalPythonRequest(), handler);

    assertThat(handler.errors)
            .containsExactly("python_stream_unavailable:failed to call python assistant stream");
}
```

新增构造参数验证测试：

```java
@Test
void rejectsBlankInternalToken() {
    assertThatThrownBy(() -> new RestPythonAssistantClient(
            "http://127.0.0.1:8000",
            1000,
            5000,
            9000,
            " "
    )).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("internal token");
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=RestPythonAssistantClientTests test
```

Expected: test compilation fails because the five-argument constructor does not exist.

- [ ] **Step 3: Implement the minimal HTTP Adapter change**

在 `RestPythonAssistantClient` 增加字段：

```java
private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

private final Duration readTimeout;
private final Duration streamReadTimeout;
private final String internalToken;
```

构造器改为：

```java
public RestPythonAssistantClient(
        @Value("${app.ai.python-base-url}") String pythonBaseUrl,
        @Value("${app.ai.connect-timeout-ms}") long connectTimeoutMs,
        @Value("${app.ai.read-timeout-ms}") long readTimeoutMs,
        @Value("${app.ai.stream-read-timeout-ms:${app.ai.stream-timeout-ms:120000}}") long streamReadTimeoutMs,
        @Value("${app.ai.python-internal-token:${app.internal-api.token}}") String internalToken
) {
    if (internalToken == null || internalToken.isBlank()) {
        throw new IllegalArgumentException("python internal token must not be blank");
    }
    this.objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(connectTimeoutMs))
            .build();
    this.chatUrl = pythonBaseUrl.replaceAll("/+$", "") + "/chat";
    this.streamChatUrl = pythonBaseUrl.replaceAll("/+$", "") + "/chat/stream";
    this.readTimeout = Duration.ofMillis(readTimeoutMs);
    this.streamReadTimeout = Duration.ofMillis(streamReadTimeoutMs);
    this.internalToken = internalToken;
}
```

同步请求增加：

```java
.header(INTERNAL_TOKEN_HEADER, internalToken)
```

流式请求改为：

```java
.timeout(streamReadTimeout)
.header("Content-Type", "application/json")
.header("Accept", "text/event-stream")
.header(INTERNAL_TOKEN_HEADER, internalToken)
```

配置改为：

```properties
app.ai.read-timeout-ms=30000
app.ai.stream-read-timeout-ms=120000
app.ai.stream-timeout-ms=120000
app.ai.python-internal-token=${APP_AI_PYTHON_INTERNAL_TOKEN:${APP_INTERNAL_API_TOKEN:dev-internal-token}}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=RestPythonAssistantClientTests test
```

Expected: all `RestPythonAssistantClientTests` pass.

- [ ] **Step 5: Commit Task 1**

```powershell
git add backend/src/main/resources/application.properties `
  backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java `
  backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestPythonAssistantClientTests.java
git commit -m "feat: secure Java Python assistant calls"
```

## Task 2: Add a Recoverable Circuit Breaker Adapter

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantResilienceConfig.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClient.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClientTests.java`
- Modify: `backend/src/main/resources/application.properties`

- [ ] **Step 1: Add the dependency declaration**

在 `pom.xml` properties 增加：

```xml
<resilience4j.version>2.4.0</resilience4j.version>
```

在 dependencies 增加 Core 模块，不添加尚未验证 Boot 4 兼容性的 Starter：

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>${resilience4j.version}</version>
</dependency>
```

- [ ] **Step 2: Write failing CircuitBreaker tests**

创建 `ResilientPythonAssistantClientTests`，包声明为：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;
```

提供测试配置工厂：

```java
private CircuitBreaker newCircuitBreaker(Duration openDuration) {
    CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(2)
            .minimumNumberOfCalls(2)
            .failureRateThreshold(100.0f)
            .waitDurationInOpenState(openDuration)
            .permittedNumberOfCallsInHalfOpenState(1)
            .build();
    return CircuitBreaker.of("python-assistant-test", config);
}
```

测试类内增加完整的最小请求工厂：

```java
private PythonChatRequest minimalPythonRequest() {
    return new PythonChatRequest(
            "req-circuit-test",
            "th_circuit_001",
            "th_circuit_001",
            "hello",
            List.of(),
            new PythonUserContext(
                    10L,
                    null,
                    null,
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null
            ),
            List.of(),
            false
    );
}
```

同步 OPEN 测试：

```java
@Test
void opensAfterConfiguredFailuresAndStopsCallingDelegate() {
    RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
    CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofSeconds(30));
    ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
    PythonChatRequest request = minimalPythonRequest();
    when(delegate.chat(request)).thenThrow(new ExternalServiceException("down"));

    assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
    assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThatThrownBy(() -> client.chat(request)).isInstanceOf(CallNotPermittedException.class);
    verify(delegate, times(2)).chat(request);
}
```

恢复测试使用 10ms OPEN 窗口，并在 25ms 后发起真实探测调用：

```java
@Test
void successfulHalfOpenProbeClosesCircuitWithoutRestart() throws Exception {
    RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
    CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofMillis(10));
    ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
    PythonChatRequest request = minimalPythonRequest();
    PythonChatResponse success = new PythonChatResponse("req", "ok", "chat", List.of());
    when(delegate.chat(request))
            .thenThrow(new ExternalServiceException("down"))
            .thenThrow(new ExternalServiceException("down"))
            .thenReturn(success);

    assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
    assertThatThrownBy(() -> client.chat(request)).isInstanceOf(ExternalServiceException.class);
    Thread.sleep(25L);

    assertThat(client.chat(request)).isEqualTo(success);
    assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
}
```

SSE 终态测试：

```java
@Test
void recordsStreamErrorAndRejectsNextStreamWhenCircuitIsOpen() {
    RestPythonAssistantClient delegate = mock(RestPythonAssistantClient.class);
    CircuitBreaker circuitBreaker = newCircuitBreaker(Duration.ofSeconds(30));
    ResilientPythonAssistantClient client = new ResilientPythonAssistantClient(delegate, circuitBreaker);
    PythonAssistantStreamHandler first = mock(PythonAssistantStreamHandler.class);
    PythonAssistantStreamHandler second = mock(PythonAssistantStreamHandler.class);
    doAnswer(invocation -> {
        invocation.<PythonAssistantStreamHandler>getArgument(1)
                .onError("python_stream_unavailable", "down");
        return null;
    }).when(delegate).streamChat(any(), any());

    client.streamChat(minimalPythonRequest(), first);
    client.streamChat(minimalPythonRequest(), first);
    client.streamChat(minimalPythonRequest(), second);

    verify(second).onError(eq("python_circuit_open"), anyString());
    verify(delegate, times(2)).streamChat(any(), any());
}
```

- [ ] **Step 3: Run the new test and verify RED**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ResilientPythonAssistantClientTests test
```

Expected: test compilation fails because `ResilientPythonAssistantClient` does not exist.

- [ ] **Step 4: Create the CircuitBreaker bean**

创建 `AssistantResilienceConfig`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.assistant.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Python AI 依赖的故障隔离配置。
 *
 * CircuitBreaker 只保护 Java 到 Python 的远程调用，不影响商品、订单、库存和支付等本地事实能力。
 */
@Configuration
public class AssistantResilienceConfig {

    @Bean
    public CircuitBreaker pythonAssistantCircuitBreaker(
            @Value("${app.ai.circuit-breaker.sliding-window-size:4}") int slidingWindowSize,
            @Value("${app.ai.circuit-breaker.minimum-number-of-calls:4}") int minimumNumberOfCalls,
            @Value("${app.ai.circuit-breaker.failure-rate-threshold:50}") float failureRateThreshold,
            @Value("${app.ai.circuit-breaker.wait-duration-ms:10000}") long waitDurationMs,
            @Value("${app.ai.circuit-breaker.half-open-permitted-calls:1}") int halfOpenPermittedCalls
    ) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .failureRateThreshold(failureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(waitDurationMs))
                .permittedNumberOfCallsInHalfOpenState(halfOpenPermittedCalls)
                .build();
        return CircuitBreaker.of("python-assistant", config);
    }
}
```

配置：

```properties
app.ai.circuit-breaker.sliding-window-size=4
app.ai.circuit-breaker.minimum-number-of-calls=4
app.ai.circuit-breaker.failure-rate-threshold=50
app.ai.circuit-breaker.wait-duration-ms=10000
app.ai.circuit-breaker.half-open-permitted-calls=1
```

- [ ] **Step 5: Implement the resilient decorator**

创建 `ResilientPythonAssistantClient`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.assistant.client;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatRequest;
import com.recommendation.intelligentoutfitrecommendationsystem.assistant.dto.PythonChatResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ExternalServiceException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 为 Python HTTP Adapter 增加同步与 SSE 共用的可恢复熔断策略。
 *
 * 该模块不生成业务降级文案，只决定远程调用是否被允许，并将终态记录到 CircuitBreaker。
 */
@Primary
@Component
public class ResilientPythonAssistantClient implements PythonAssistantClient, PythonAssistantStreamClient {

    private final RestPythonAssistantClient delegate;
    private final CircuitBreaker circuitBreaker;

    public ResilientPythonAssistantClient(
            RestPythonAssistantClient delegate,
            CircuitBreaker pythonAssistantCircuitBreaker
    ) {
        this.delegate = delegate;
        this.circuitBreaker = pythonAssistantCircuitBreaker;
    }

    @Override
    public PythonChatResponse chat(PythonChatRequest request) {
        return circuitBreaker.executeSupplier(() -> delegate.chat(request));
    }

    @Override
    public void streamChat(PythonChatRequest request, PythonAssistantStreamHandler handler) {
        if (!circuitBreaker.tryAcquirePermission()) {
            handler.onError("python_circuit_open", "python assistant circuit is open");
            return;
        }
        long startedAt = System.nanoTime();
        AtomicBoolean terminalRecorded = new AtomicBoolean();
        PythonAssistantStreamHandler recordingHandler = new RecordingHandler(
                handler,
                terminalRecorded,
                startedAt
        );
        try {
            delegate.streamChat(request, recordingHandler);
            if (terminalRecorded.compareAndSet(false, true)) {
                ExternalServiceException exception =
                        new ExternalServiceException("python assistant stream ended without terminal event");
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
                handler.onError("python_stream_incomplete", "python assistant stream ended unexpectedly");
            }
        } catch (RuntimeException exception) {
            if (terminalRecorded.compareAndSet(false, true)) {
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
            }
            throw exception;
        }
    }

    private long elapsedNanos(long startedAt) {
        return System.nanoTime() - startedAt;
    }

    private final class RecordingHandler implements PythonAssistantStreamHandler {
        private final PythonAssistantStreamHandler delegate;
        private final AtomicBoolean terminalRecorded;
        private final long startedAt;

        private RecordingHandler(
                PythonAssistantStreamHandler delegate,
                AtomicBoolean terminalRecorded,
                long startedAt
        ) {
            this.delegate = delegate;
            this.terminalRecorded = terminalRecorded;
            this.startedAt = startedAt;
        }

        @Override
        public void onToken(String content) {
            delegate.onToken(content);
        }

        @Override
        public void onDone(PythonChatResponse response) {
            if (terminalRecorded.compareAndSet(false, true)) {
                circuitBreaker.onSuccess(elapsedNanos(startedAt), TimeUnit.NANOSECONDS);
            }
            delegate.onDone(response);
        }

        @Override
        public void onError(String code, String message) {
            if (terminalRecorded.compareAndSet(false, true)) {
                ExternalServiceException exception = new ExternalServiceException(message);
                circuitBreaker.onError(elapsedNanos(startedAt), TimeUnit.NANOSECONDS, exception);
            }
            delegate.onError(code, message);
        }
    }
}
```

- [ ] **Step 6: Run the focused test and verify GREEN**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=ResilientPythonAssistantClientTests test
```

Expected: all CircuitBreaker tests pass, including OPEN and successful HALF_OPEN recovery.

- [ ] **Step 7: Commit Task 2**

```powershell
git add backend/pom.xml `
  backend/src/main/resources/application.properties `
  backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantResilienceConfig.java `
  backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClient.java `
  backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/ResilientPythonAssistantClientTests.java
git commit -m "feat: add recoverable Python circuit breaker"
```

## Task 3: Remove the Permanent Local Fallback Guard

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

- [ ] **Step 1: Replace stateful fallback tests with behavior tests**

删除依赖 `new AssistantFallbackService(threshold)` 和 `recordPythonFailure` 的测试设置。

将同步 Guard 测试改为：

```java
@Test
void returnsSafeFallbackWhenResilientClientRejectsCall() {
    AssistantChatRequest request = new AssistantChatRequest(
            "th_existing",
            "recommend a jacket",
            "outerwear",
            "commute",
            null,
            null,
            "regular",
            null
    );
    AssistantContext context = new AssistantContext(
            null,
            null,
            null,
            null,
            List.of(),
            List.of()
    );
    when(assistantContextService.buildContext(10L, "th_existing", request)).thenReturn(context);
    when(pythonAssistantClient.chat(any(PythonChatRequest.class)))
            .thenThrow(new ExternalServiceException("python circuit is open"));

    AssistantChatResponse response = newAssistantService().chat(10L, request);

    assertThat(response.answer()).contains("AI 导购暂时不可用");
    assertThat(response.recommendedItems()).isEmpty();
    verify(pythonAssistantClient).chat(any(PythonChatRequest.class));
}
```

流式测试改为让 `pythonAssistantStreamClient` 主动回调：

```java
doAnswer(invocation -> {
    invocation.<PythonAssistantStreamHandler>getArgument(1)
            .onError("python_circuit_open", "python assistant circuit is open");
    return null;
}).when(pythonAssistantStreamClient).streamChat(any(), any());
```

断言 Java 返回现有安全 `error` 事件，并且不写入 assistant 消息。

- [ ] **Step 2: Run AssistantService tests and verify RED**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantServiceTests test
```

Expected: compilation fails until stateful fallback methods and constructors are removed consistently.

- [ ] **Step 3: Make AssistantFallbackService stateless**

删除：

```java
private final AtomicInteger consecutiveFailures;
private final int failureThreshold;
public boolean shouldBypassPython();
public void recordPythonSuccess();
public void recordPythonFailure(Throwable ignoredFailure);
```

保留 Spring 默认无参构造和以下两个职责：

```java
public PythonChatResponse chatFallbackResponse(PythonChatRequest pythonRequest) {
    return new PythonChatResponse(
            pythonRequest.requestId(),
            SAFE_CHAT_FALLBACK,
            FALLBACK_INTENT,
            List.of()
    );
}

public AssistantStreamErrorEvent streamFallbackError() {
    return new AssistantStreamErrorEvent("python_stream_unavailable", SAFE_STREAM_FALLBACK);
}
```

随后删除 `@Value`、`AtomicInteger` import 和旧属性 `app.ai.fallback.failure-threshold`。

- [ ] **Step 4: Simplify AssistantService failure flow**

`streamChat` 删除调用 Python 前的 `shouldBypassPython()` 分支。CircuitBreaker OPEN 时，装饰 Client 会快速产生 `python_circuit_open` error。

调用点改为：

```java
PythonChatResponse pythonResponse = callPythonOrFallback(pythonRequest);
```

`callPythonOrFallback` 改为：

```java
private PythonChatResponse callPythonOrFallback(PythonChatRequest pythonRequest) {
    try {
        PythonChatResponse pythonResponse = pythonAssistantClient.chat(pythonRequest);
        requireAnswer(pythonResponse);
        return pythonResponse;
    } catch (RuntimeException exception) {
        return assistantFallbackService.chatFallbackResponse(pythonRequest);
    }
}
```

测试帮助方法统一使用无状态 Fallback：

```java
private AssistantService newAssistantService() {
    return new AssistantService(
            conversationService,
            assistantContextService,
            assistantRateLimitService,
            pythonAssistantClient,
            pythonAssistantStreamClient,
            new AssistantFallbackService(),
            directExecutor,
            120_000L
    );
}
```

删除接收 `AssistantFallbackService` 参数的旧重载帮助方法。

`ForwardingStreamHandler.onDone` 删除 `recordPythonSuccess()`；`onDone` 的空答案分支和 `onError` 删除 `recordPythonFailure()`。熔断装饰 Adapter 已经在终态回调前记录结果。

- [ ] **Step 5: Run Assistant tests and verify GREEN**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantServiceTests,ResilientPythonAssistantClientTests test
```

Expected: all selected tests pass; OPEN CircuitBreaker 仍返回安全同步/流式降级，但不再永久旁路。

- [ ] **Step 6: Commit Task 3**

```powershell
git add backend/src/main/resources/application.properties `
  backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantFallbackService.java `
  backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java `
  backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java
git commit -m "refactor: make AI fallback stateless"
```

## Task 4: Propagate MDC Through the SSE Executor

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantStreamingConfig.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantStreamingConfigTests.java`

- [ ] **Step 1: Write the failing MDC propagation test**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.assistant;

import com.recommendation.intelligentoutfitrecommendationsystem.assistant.config.AssistantStreamingConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantStreamingConfigTests {

    private ThreadPoolTaskExecutor executor;

    @AfterEach
    void cleanUp() {
        MDC.clear();
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Test
    void propagatesAndClearsMdcForStreamingTasks() throws Exception {
        Executor configured = new AssistantStreamingConfig().assistantStreamingExecutor();
        executor = (ThreadPoolTaskExecutor) configured;
        MDC.put("requestId", "req-stream-context");
        CompletableFuture<String> first = new CompletableFuture<>();

        executor.execute(() -> first.complete(MDC.get("requestId")));

        assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("req-stream-context");
        MDC.clear();
        CompletableFuture<String> second = new CompletableFuture<>();
        executor.execute(() -> second.complete(MDC.get("requestId")));
        assertThat(second.get(1, TimeUnit.SECONDS)).isNull();
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantStreamingConfigTests test
```

Expected: first future returns `null` because MDC is ThreadLocal and no TaskDecorator exists.

- [ ] **Step 3: Add the TaskDecorator**

在 `AssistantStreamingConfig` 增加：

```java
import org.slf4j.MDC;

import java.util.Map;
```

初始化前设置：

```java
executor.setTaskDecorator(task -> {
    Map<String, String> callerContext = MDC.getCopyOfContextMap();
    return () -> {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        try {
            if (callerContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(callerContext);
            }
            task.run();
        } finally {
            if (previousContext == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousContext);
            }
        }
    };
});
```

- [ ] **Step 4: Run the test and verify GREEN**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantStreamingConfigTests test
```

Expected: MDC request ID is propagated to the first task and absent from the second task.

- [ ] **Step 5: Commit Task 4**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/AssistantStreamingConfig.java `
  backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantStreamingConfigTests.java
git commit -m "fix: propagate AI stream logging context"
```

## Task 5: Full Verification and Documentation Sync

**Files:**
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [ ] **Step 1: Run all focused AI tests**

```powershell
cd backend
.\mvnw.cmd -Dtest=RestPythonAssistantClientTests,ResilientPythonAssistantClientTests,AssistantServiceTests,AssistantStreamingConfigTests test
```

Expected: all focused tests pass.

- [ ] **Step 2: Run the full Java quality gate**

```powershell
cd backend
.\mvnw.cmd verify
```

Expected: tests and Checkstyle pass. If Verify fails only because the user's uncommitted learning Demo remains under production source, do not rewrite or delete it silently; report the exact files and execute the separate learning-code isolation plan before claiming the quality gate is green.

- [ ] **Step 3: Review dependency and secret output**

```powershell
cd backend
.\mvnw.cmd dependency:tree "-Dincludes=io.github.resilience4j:*"
git grep -n "dev-internal-token\|APP_AI_PYTHON_INTERNAL_TOKEN" -- . ":(exclude)backend/target"
```

Expected:

- only Resilience4j Core modules required by `resilience4j-circuitbreaker` are present;
- no runtime log statement prints the internal Token;
- the development default appears only in configuration/documentation, not in emitted test output.

- [ ] **Step 4: Record the shipped Java behavior and remaining cross-service gate**

在设计文档状态区增加：

```markdown
### Java AI Client Reliability 实施状态

- Java 已发送 `X-Internal-Token`；
- 同步与 SSE 使用独立请求超时；
- CircuitBreaker 支持 CLOSED/OPEN/HALF_OPEN 自动恢复；
- Fallback 内容模块无可变熔断状态；
- SSE Executor 传播并清理 MDC；
- Python 端 Token 强制校验与跨服务 Smoke Test 尚未在本 Java 分支验收。
```

不得把 Python 身份认证标记为端到端完成，直到另一台电脑的 Python 分支强制校验 Token 且跨服务 Smoke Test 通过。

- [ ] **Step 5: Commit documentation sync**

```powershell
git add docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md
git commit -m "docs: record Java AI reliability baseline"
```

## Definition of Done

- Java 同步和 SSE 请求都发送内部 Token；
- Stream HTTP 请求真正使用独立的 120 秒读取超时；
- CircuitBreaker 达到失败阈值后进入 OPEN；
- 等待窗口结束后通过 HALF_OPEN 探测自动恢复；
- OPEN 时 Java 同步与 SSE 都快速返回现有安全降级；
- `AssistantFallbackService` 不再保存全局可变失败计数；
- SSE 线程能够读取原始 request ID，任务结束后不污染下一任务；
- 聚焦测试全部通过；
- Maven Verify 结果被如实记录；
- 未修改 Python/LangGraph 运行时代码；
- 未覆盖用户未提交的 learning Demo。
