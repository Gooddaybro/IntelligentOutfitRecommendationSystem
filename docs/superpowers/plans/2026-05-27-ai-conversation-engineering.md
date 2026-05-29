# AI 会话联动与工程化底座 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐支撑 AI 推荐联动的工程化底座，并打通“用户 -> 会话 -> Java assistant-service -> Python AI 服务”的第一版同步 HTTP 闭环。

**Architecture:** 本阶段采用模块化单体继续演进。先补 Docker Compose、CI、MDC requestId、Testcontainers、REST Docs 等基础能力，再新增 conversation-service 保存会话与消息历史，最后由 assistant-service 组装用户画像、会话历史、推荐候选商品并同步调用 Python AI 服务。

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Web MVC, Spring Security OAuth2 Resource Server, MyBatis XML, Flyway, MySQL 8.0, Testcontainers, Docker Compose, GitHub Actions, SLF4J MDC, Logback, Spring REST Docs, MockMvc, Reqable.

---

## 0. 当前执行状态（2026-05-28）

- 已完成：Docker Compose、MDC requestId、GitHub Actions、Testcontainers MySQL 迁移测试、Spring REST Docs auth 片段。
- 已完成：conversation-service，包括 `chat_session`、`chat_message`、Mapper、Service、Controller 和测试。
- 已完成：assistant-service 第一版同步 HTTP 链路，包括 Java 调 Python `/chat`、上下文组装、消息落库和测试。
- 未进入本阶段：购物车、订单、支付、SSE / WebSocket、MQ 异步推荐任务。

---

## 1. 阶段边界

### 本阶段实现

- Docker Compose 本地 MySQL 环境。
- GitHub Actions 自动运行 Maven 测试。
- MDC requestId 链路日志追踪。
- Testcontainers + MySQL 集成测试基类和关键链路测试。
- Spring REST Docs 覆盖 auth/user/conversation/assistant 核心接口。
- conversation-service：会话、消息历史、thread_id、当前用户隔离。
- assistant-service：同步 HTTP 调 Python AI 服务，保存用户消息和 assistant 回复。
- Reqable 测试文档和功能对照表更新。

### 本阶段不实现

- 购物车。
- 订单。
- 支付。
- SSE / WebSocket 流式返回。
- MQ 异步推荐任务。
- Python AI 服务内部算法改造。

### 设计原则

- 普通用户接口继续使用 `Authorization: Bearer <accessToken>`。
- Python AI 服务调用 Java internal API 仍使用 `X-Internal-Token`。
- Java assistant-service 是 AI 网关，不实现推荐算法。
- 第一版只做同步 HTTP，等普通闭环稳定后再做 SSE 和 MQ。
- 会话和消息必须绑定当前 `userId`，禁止通过请求参数访问他人会话。

---

## 2. 文件结构规划

### 工程化文件

- Create: `docker-compose.yml`
- Create: `.github/workflows/ci.yml`
- Create: `src/main/resources/logback-spring.xml`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptor.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/WebMvcConfig.java`
- Modify: `pom.xml`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptorTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/BaseMySqlContainerTest.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`

### conversation-service

- Create: `src/main/resources/db/migration/V4__conversation_schema.sql`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/api/ConversationController.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/mapper/ConversationMapper.java`
- Create: `src/main/resources/mapper/conversation/ConversationMapper.xml`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatSession.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatMessage.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/CreateConversationRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/ConversationResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/MessageResponse.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationMapperTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationControllerTests.java`

### assistant-service

- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/api/AssistantController.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/PythonAssistantClient.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/PythonAssistantProperties.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/PythonAssistantConfig.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/ExternalServiceException.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantControllerTests.java`

### 文档

- Modify: `docs/backend-feature-mapping.md`
- Modify: `docs/api-testing-with-reqable.md`
- Modify: `docs/architecture/java-ai-clothing-mall-architecture.md`

---

## 3. API 设计

### conversation-service API

| API | 方法 | 鉴权 | 说明 |
|---|---|---|---|
| `/api/conversations` | POST | Bearer JWT | 创建当前用户的新会话 |
| `/api/conversations` | GET | Bearer JWT | 查询当前用户会话列表 |
| `/api/conversations/{threadId}/messages` | GET | Bearer JWT | 查询当前用户某个会话的消息历史 |
| `/api/conversations/{threadId}` | DELETE | Bearer JWT | 归档当前用户某个会话 |

### assistant-service API

| API | 方法 | 鉴权 | 说明 |
|---|---|---|---|
| `/api/assistant/chat` | POST | Bearer JWT | 同步调用 Python AI，保存 user/assistant 消息并返回 JSON |

`POST /api/assistant/chat` 请求示例：

```json
{
  "threadId": null,
  "message": "我想买一件适合秋天通勤的外套",
  "category": "外套",
  "style": "commute",
  "season": "autumn",
  "material": null,
  "fit": null,
  "budgetMax": 400
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "threadId": "th_20260527_a1b2c3",
    "answer": "推荐你优先看通勤夹克，版型利落，适合秋季叠穿。",
    "recommendedSpuIds": [1002],
    "candidatesCount": 1
  },
  "errorCode": null,
  "message": "ok"
}
```

---

## 4. 数据库设计

### V4__conversation_schema.sql

```sql
CREATE TABLE chat_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    thread_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    title VARCHAR(128) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_message_at DATETIME(6) NULL,
    UNIQUE KEY uk_chat_session_thread (thread_id),
    KEY idx_chat_session_user_status (user_id, status),
    CONSTRAINT fk_chat_session_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    message_status VARCHAR(32) NOT NULL DEFAULT 'succeeded',
    request_id VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_chat_message_session_created (session_id, created_at),
    KEY idx_chat_message_user_created (user_id, created_at),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
);
```

说明：

- `thread_id` 对外暴露，避免前端直接使用数据库主键。
- `role` 第一版限定为 `user`、`assistant`、`system`。
- `message_status` 第一版使用 `succeeded`、`failed`。
- 推荐结果明细本阶段先不单独建表，assistant 响应中先保存文本和 `recommendedSpuIds`；后续做转化分析时再扩展 `assistant_recommendation` 表。

---

## 5. 任务拆分

### Task 1: Docker Compose 本地 MySQL 环境

**Files:**
- Create: `docker-compose.yml`
- Modify: `docs/api-testing-with-reqable.md`

- [ ] **Step 1: 创建 Docker Compose 文件**

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: intelligent_outfit_mysql
    ports:
      - "3307:3306"
    environment:
      MYSQL_DATABASE: intelligent_outfit
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}
      TZ: Asia/Shanghai
    command:
      - --character-set-server=utf8mb4
      - --collation-server=utf8mb4_unicode_ci
    volumes:
      - intelligent_outfit_mysql_data:/var/lib/mysql

volumes:
  intelligent_outfit_mysql_data:
```

- [ ] **Step 2: 验证本地 MySQL 可启动**

Run:

```powershell
docker compose up -d mysql
docker compose ps
```

Expected:

```text
intelligent_outfit_mysql   mysql:8.0   Up
```

- [ ] **Step 3: 启动 Java 应用验证 Flyway 迁移**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd spring-boot:run
```

Expected:

```text
Successfully applied 4 migrations
Started IntelligentOutfitRecommendationSystemApplication
```

---

### Task 2: MDC requestId 链路日志

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptor.java`
- Create: `src/main/resources/logback-spring.xml`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/WebMvcConfig.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/logging/MdcRequestIdInterceptorTests.java`

- [ ] **Step 1: 写 requestId 拦截器测试**

```java
@Test
void preHandleUsesIncomingRequestIdAndWritesResponseHeader() throws Exception {
    MdcRequestIdInterceptor interceptor = new MdcRequestIdInterceptor();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.addHeader("X-Request-Id", "req-test-001");

    interceptor.preHandle(request, response, new Object());

    assertThat(MDC.get("requestId")).isEqualTo("req-test-001");
    assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-test-001");

    interceptor.afterCompletion(request, response, new Object(), null);
    assertThat(MDC.get("requestId")).isNull();
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=MdcRequestIdInterceptorTests" test
```

Expected: 编译失败，提示 `MdcRequestIdInterceptor` 不存在。

- [ ] **Step 3: 实现 MdcRequestIdInterceptor**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class MdcRequestIdInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_REQUEST_ID = "requestId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_REQUEST_ID, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(MDC_REQUEST_ID);
    }
}
```

- [ ] **Step 4: 修改 WebMvcConfig 注册拦截器**

要求：

```java
registry.addInterceptor(mdcRequestIdInterceptor)
        .addPathPatterns("/**")
        .order(0);

registry.addInterceptor(internalApiInterceptor)
        .addPathPatterns("/internal/**")
        .order(1);
```

- [ ] **Step 5: 新增 logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <property name="CONSOLE_LOG_PATTERN"
              value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [reqId=%X{requestId:-}] [%thread] %logger{36} - %msg%n"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

- [ ] **Step 6: 运行测试**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=MdcRequestIdInterceptorTests" test
```

Expected: PASS。

---

### Task 3: Testcontainers MySQL 集成测试

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/BaseMySqlContainerTest.java`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/support/MySqlFlywayMigrationTests.java`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/UserAuthMapperMySqlTests.java`

- [ ] **Step 1: pom.xml 添加测试依赖**

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-testcontainers</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 创建 Testcontainers 基类**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.support;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("mysql")
@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
public abstract class BaseMySqlContainerTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("intelligent_outfit")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
    }
}
```

- [ ] **Step 3: 创建 Flyway 迁移验证测试**

```java
class MySqlFlywayMigrationTests extends BaseMySqlContainerTest {

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void appliesAllMigrationsOnRealMySql() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1",
                Integer.class
        );

        assertThat(count).isGreaterThanOrEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_session", Integer.class)).isZero();
    }
}
```

- [ ] **Step 4: 创建真实 MySQL Mapper 测试**

覆盖 `UserAuthMapper` 和 `ConversationMapper` 的真实 MySQL SQL 映射。优先验证：

- `user_account` 插入和角色绑定。
- `refresh_token` 插入和撤销。
- `chat_session` 插入和按 `thread_id + user_id` 查询。
- `chat_message` 按会话时间线排序。

- [ ] **Step 5: 运行 MySQL 集成测试**

Run:

```powershell
.\mvnw.cmd -q "-Dgroups=mysql" test
```

Expected: Docker 可用时 PASS。若本地未启动 Docker，应清晰失败并提示 Testcontainers 无法连接 Docker。

---

### Task 4: GitHub Actions CI

**Files:**
- Create: `.github/workflows/ci.yml`

- [ ] **Step 1: 创建 CI 工作流**

```yaml
name: Java CI

on:
  push:
    branches: [ master ]
  pull_request:
    branches: [ master ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Make Maven wrapper executable
        run: chmod +x mvnw

      - name: Run tests
        run: ./mvnw -q test
```

- [ ] **Step 2: 本地验证 workflow YAML 语法**

Run:

```powershell
Get-Content .github/workflows/ci.yml
```

Expected: 文件存在，无缩进错误。

---

### Task 5: Spring REST Docs 接口契约文档

**Files:**
- Modify: `pom.xml`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/AuthControllerTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/user/UserProfileControllerTests.java`
- Create: `src/docs/asciidoc/api.adoc`

- [ ] **Step 1: 添加 REST Docs 依赖**

```xml
<dependency>
    <groupId>org.springframework.restdocs</groupId>
    <artifactId>spring-restdocs-mockmvc</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **Step 2: 在核心 Controller 测试启用 REST Docs**

在 `AuthControllerTests` 和 `UserProfileControllerTests` 上添加：

```java
@AutoConfigureRestDocs(outputDir = "target/generated-snippets")
```

- [ ] **Step 3: 为登录接口生成文档片段**

```java
mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
        .andExpect(status().isOk())
        .andDo(document("auth-login"));
```

- [ ] **Step 4: 创建 api.adoc 聚合文档**

```adoc
= Intelligent Outfit Recommendation System API

== Auth

include::{snippets}/auth-login/http-request.adoc[]
include::{snippets}/auth-login/http-response.adoc[]
```

- [ ] **Step 5: 运行测试生成片段**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AuthControllerTests,UserProfileControllerTests" test
```

Expected:

```text
target/generated-snippets/auth-login/http-request.adoc
target/generated-snippets/auth-login/http-response.adoc
```

---

### Task 6: conversation-service 数据库与 Mapper

**Files:**
- Create: `src/main/resources/db/migration/V4__conversation_schema.sql`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatSession.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/model/ChatMessage.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/mapper/ConversationMapper.java`
- Create: `src/main/resources/mapper/conversation/ConversationMapper.xml`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationMapperTests.java`

- [ ] **Step 1: 先写 Mapper 测试**

测试目标：

```java
@Test
void insertsSessionAndMessagesForUserTimeline() {
    Long userId = createUser();
    ChatSession session = new ChatSession();
    session.setThreadId("th_mapper_001");
    session.setUserId(userId);
    session.setTitle("秋季通勤外套");
    session.setStatus("active");
    conversationMapper.insertSession(session);

    conversationMapper.insertMessage(message(session.getId(), userId, "user", "推荐一件通勤外套"));
    conversationMapper.insertMessage(message(session.getId(), userId, "assistant", "可以看通勤夹克"));

    assertThat(conversationMapper.findSessionByThreadIdAndUserId("th_mapper_001", userId)).isNotNull();
    assertThat(conversationMapper.findMessagesBySessionId(session.getId()))
            .extracting("role")
            .containsExactly("user", "assistant");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=ConversationMapperTests" test
```

Expected: 编译失败，提示 conversation 类不存在。

- [ ] **Step 3: 创建 V4 迁移脚本**

使用本文第 4 节 SQL。

- [ ] **Step 4: 创建 model 和 Mapper 接口**

`ConversationMapper` 至少包含：

```java
void insertSession(ChatSession session);
ChatSession findSessionByThreadIdAndUserId(@Param("threadId") String threadId, @Param("userId") Long userId);
List<ChatSession> findSessionsByUserId(@Param("userId") Long userId);
void archiveSession(@Param("threadId") String threadId, @Param("userId") Long userId);
void insertMessage(ChatMessage message);
List<ChatMessage> findMessagesBySessionId(@Param("sessionId") Long sessionId);
```

- [ ] **Step 5: 创建 XML SQL**

要求：

- `insertSession` 使用 `useGeneratedKeys=true`。
- `findSessionsByUserId` 只返回当前用户 `status='active'` 会话。
- `findMessagesBySessionId` 按 `created_at ASC, id ASC` 排序。
- `archiveSession` 必须同时匹配 `thread_id` 和 `user_id`。

- [ ] **Step 6: 运行 Mapper 测试**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=ConversationMapperTests" test
```

Expected: PASS。

---

### Task 7: conversation-service Service 与 Controller

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/service/ConversationService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/api/ConversationController.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/CreateConversationRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/ConversationResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/dto/MessageResponse.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/conversation/ConversationControllerTests.java`

- [ ] **Step 1: 写 Controller 测试**

覆盖：

- 未登录访问 `/api/conversations` 返回 401。
- 登录用户可以创建会话。
- 登录用户只能查询自己的会话。
- 归档会话后列表不再返回。

核心断言：

```java
mockMvc.perform(post("/api/conversations")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"秋季外套\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.threadId").isNotEmpty())
        .andExpect(jsonPath("$.data.title").value("秋季外套"));
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=ConversationControllerTests" test
```

Expected: 404 或编译失败。

- [ ] **Step 3: 实现 ConversationService**

要求：

- `createConversation(Long userId, String title)` 生成 `threadId`，格式为 `th_` + 32 位随机值。
- `listConversations(Long userId)` 只查当前用户。
- `getMessages(Long userId, String threadId)` 先按 `threadId + userId` 查 session，再查 message。
- `archiveConversation(Long userId, String threadId)` 软归档。
- 提供包内方法 `appendMessage(Long userId, String threadId, String role, String content, String status, String requestId)` 给 assistant-service 复用。

- [ ] **Step 4: 实现 ConversationController**

路由：

```text
POST /api/conversations
GET /api/conversations
GET /api/conversations/{threadId}/messages
DELETE /api/conversations/{threadId}
```

- [ ] **Step 5: 运行测试**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=ConversationControllerTests" test
```

Expected: PASS。

---

### Task 8: assistant-service Python HTTP 客户端

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/PythonAssistantProperties.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/config/PythonAssistantConfig.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/PythonAssistantClient.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/client/RestPythonAssistantClient.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatResponse.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/ExternalServiceException.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: 添加 Python 服务配置**

```properties
app.ai.python-base-url=http://127.0.0.1:8000
app.ai.connect-timeout-ms=3000
app.ai.read-timeout-ms=30000
```

- [ ] **Step 2: 创建属性类**

```java
@Data
@ConfigurationProperties(prefix = "app.ai")
public class PythonAssistantProperties {
    private String pythonBaseUrl;
    private int connectTimeoutMs = 3000;
    private int readTimeoutMs = 30000;
}
```

- [ ] **Step 3: 创建 PythonAssistantClient 接口**

```java
public interface PythonAssistantClient {
    PythonChatResponse chat(PythonChatRequest request);
}
```

- [ ] **Step 4: 实现 RestPythonAssistantClient**

要求：

- POST 到 Python `/chat`。
- Python 不可用时抛出 `ExternalServiceException("python ai service unavailable")`。
- 不吞掉 requestId，日志中保留当前 MDC。

第一版 Python 返回契约：

```json
{
  "answer": "推荐通勤夹克",
  "recommendedSpuIds": [1002]
}
```

- [ ] **Step 5: GlobalExceptionHandler 增加 502**

```java
@ExceptionHandler(ExternalServiceException.class)
@ResponseStatus(HttpStatus.BAD_GATEWAY)
public ApiResponse<Void> handleExternalService(ExternalServiceException exception) {
    return ApiResponse.error("external_service_error", exception.getMessage());
}
```

---

### Task 9: assistant-service 上下文组装与同步聊天

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/api/AssistantController.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatRequest.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantControllerTests.java`

- [ ] **Step 1: 写 AssistantService 单元测试**

验证：

- 没有 `threadId` 时自动创建会话。
- 保存用户消息。
- 查询用户画像和推荐候选。
- 调用 Python client。
- 保存 assistant 回复。
- 返回 `threadId`、`answer`、`recommendedSpuIds`、`candidatesCount`。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AssistantServiceTests" test
```

Expected: 编译失败。

- [ ] **Step 3: 实现 AssistantContextService**

职责：

- 读取 `UserProfileService.getProfile(userId)`。
- 读取 `UserProfileService.getBodyData(userId)`。
- 读取 `UserProfileService.getPreferences(userId)`。
- 调用 `ProductCatalogService.findRecommendationCandidates(query)`。
- 读取 `ConversationService.getMessages(userId, threadId)`。
- 组装 `PythonChatRequest`。

- [ ] **Step 4: 实现 AssistantService**

核心流程：

```text
1. 校验 message 不为空。
2. threadId 为空则创建会话。
3. 保存 role=user 的消息。
4. 组装 PythonChatRequest。
5. 调 PythonAssistantClient.chat。
6. 保存 role=assistant 的消息。
7. 返回 AssistantChatResponse。
```

- [ ] **Step 5: 实现 AssistantController**

```java
@PostMapping("/chat")
public ApiResponse<AssistantChatResponse> chat(
        Authentication authentication,
        @Valid @RequestBody AssistantChatRequest request
) {
    CurrentUser currentUser = CurrentUser.from(authentication);
    return ApiResponse.ok(assistantService.chat(currentUser.userId(), request));
}
```

- [ ] **Step 6: 写 AssistantControllerTests**

覆盖：

- 未登录返回 401。
- 登录后调用 `/api/assistant/chat` 返回 200。
- Python client 抛异常时返回 502。
- user/assistant 消息都能在 `/api/conversations/{threadId}/messages` 查到。

- [ ] **Step 7: 运行测试**

Run:

```powershell
.\mvnw.cmd -q "-Dtest=AssistantServiceTests,AssistantControllerTests" test
```

Expected: PASS。

---

### Task 10: 文档同步与 Reqable 验收

**Files:**
- Modify: `docs/backend-feature-mapping.md`
- Modify: `docs/api-testing-with-reqable.md`
- Modify: `docs/architecture/java-ai-clothing-mall-architecture.md`

- [ ] **Step 1: 更新功能对照表**

新增已实现项：

- Docker Compose。
- MDC requestId。
- GitHub Actions。
- Testcontainers MySQL 关键链路测试。
- conversation-service。
- assistant-service 同步 HTTP。

- [ ] **Step 2: 更新 Reqable 文档**

新增测试流程：

```text
1. 注册登录拿 accessToken。
2. 更新用户画像和偏好。
3. POST /api/conversations 创建会话。
4. POST /api/assistant/chat 发起 AI 推荐。
5. GET /api/conversations/{threadId}/messages 查看消息历史。
```

- [ ] **Step 3: 更新架构文档**

标注：

- Phase 2 已完成普通 HTTP AI 闭环。
- SSE 和 MQ 仍属于后续阶段。
- cart/order/payment 仍属于后续电商闭环。

---

### Task 11: 全量验证

**Files:**
- No code changes.

- [ ] **Step 1: 运行完整测试**

Run:

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q test
```

Expected: PASS。

- [ ] **Step 2: Docker Compose 验证**

Run:

```powershell
docker compose up -d mysql
.\mvnw.cmd spring-boot:run
```

Expected:

- MySQL 容器启动成功。
- Java 应用启动成功。
- Flyway schema version 至少为 4。

- [ ] **Step 3: Reqable 手动验证**

按 `docs/api-testing-with-reqable.md` 完成：

- 注册。
- 登录。
- 更新画像。
- 创建会话。
- AI 同步问答。
- 查询消息历史。

---

## 6. 验收标准

本阶段完成后，项目应满足：

- 本地可用 `docker compose up -d mysql` 启动 MySQL。
- GitHub Actions 可自动运行测试。
- 每个 HTTP 请求日志都有 `requestId`。
- 关键 MyBatis/Flyway 链路可在真实 MySQL Testcontainers 中验证。
- REST Docs 能生成至少 auth/user/conversation/assistant 的请求响应片段。
- 当前用户可以创建会话、查询会话列表、查询消息历史、归档会话。
- 当前用户不能访问其他用户的会话和消息。
- Java 可同步调用 Python `/chat`，并把 user/assistant 消息保存到数据库。
- `/api/assistant/chat` 第一版返回普通 JSON，不做 SSE/WebSocket。
- 购物车、订单、支付、MQ 不进入本阶段。

---

## 7. 后续阶段建议

Phase 2 完成后，再进入：

1. SSE 流式聊天：复用 assistant-service 和 conversation-service，不重做业务链路。
2. cart-service：推荐结果加购。
3. order-service：订单创建、库存锁定、防超卖。
4. mock-payment：模拟支付成功、库存核销。
5. MQ 异步推荐：处理复杂评测报告或多轮长任务。
