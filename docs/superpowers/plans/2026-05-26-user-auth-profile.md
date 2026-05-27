# 简历级生产化用户认证与画像模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现具备“简历级、生产级”标准的用户注册登录、双 Token 鉴权（Access JWT + Refresh Token 数据库控制）、登录日志审计、当前用户信息、用户基础资料、身体数据和穿衣偏好模块，为后续 AI 推荐提供安全、健壮的用户画像上下文。

**Architecture:** 采用 Spring Boot 4.0.6 模块化单体结构。
1. **两套鉴权职责分离**：
   - 内部 AI 服务或微服务间路由：`/internal/**` 继续使用 `X-Internal-Token` Header。
   - 普通商城用户 API 路由：使用 Spring Security OAuth2 Resource Server + Nimbus JWT 进行轻量原生 JWT 解码与安全拦截，接口访问需携带 `Authorization: Bearer <accessToken>`。
2. **Access + Refresh Token 双令牌模型**：
   - `accessToken`：短效 JWT（有效期 15-30 分钟），无状态，用于 `/api/me/**` 等常规接口鉴权。
   - `refreshToken`：长效随机字符串（有效期 7-30 天），有状态，存储于数据库 `refresh_token` 表中进行单设备/多设备追踪、撤销与状态控制。
3. **数据访问层**：基于 Flyway 管理 MySQL 8.0 表演进，使用 MyBatis Mapper 接口 + XML 映射进行动态数据持久化。
4. **工程化强化**：集成 Testcontainers 进行真实 MySQL 环境集成测试、Docker Compose 统一本地环境、MDC 链路 requestId 追踪、API 文档以及 GitHub Actions 自动化 CI。

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Framework 7, Spring Security 7（由 Boot 4 管理版本）, OAuth2 Resource Server + JOSE (Nimbus JWT), MyBatis Spring Boot Starter 4.0.0, MySQL 8.0, Testcontainers, Flyway, Docker Compose, GitHub Actions, SLF4J + Logback, spring-boot-starter-validation, Spring REST Docs 4.0, Reqable.

---

## 1. 范围边界

### Phase 1A: 用户注册登录与双 Token 鉴权
- 用户注册与 BCrypt 密码单向 Hash 加密。
- 用户登录与 **AccessToken(JWT) + RefreshToken(数据库哈希存根)** 签发。
- Token 刷新 (`POST /api/auth/refresh`)。
- 退出登录 (`POST /api/auth/logout`)，彻底将对应的 `refreshToken` 标记为 `revoked`（失效）。
- 整合 Spring Security OAuth2 Resource Server + Nimbus JWT 拦截普通用户请求。
- 登录日志 `login_log` 审计记录。

### Phase 1B: 用户画像/身体数据/穿衣偏好 (DTO & Validation)
- 获取当前登录用户简要信息 (`GET /api/users/me`)。
- 用户基础资料 (`UserProfile`) 查询与更新（昵称、头像、性别、生日）。
- 用户身体数据 (`UserBodyData`) 查询与更新（身高、体重、三围、肩宽、偏好版型等），用于 AI 尺码推荐。
- 用户偏好数据 (`UserPreferences`) 查询与更新（风格偏好、颜色偏好、不喜欢颜色、品类偏好、预算区间等），用于 AI 个性化搭配推荐。
- 使用 `@Validated` + DTO 字段注解（`@NotBlank`, `@DecimalMin`, `@Size` 等）进行严格输入校验。

### Phase 1C: 工程化与测试强化 (简历亮点)
- **MDC requestId**：配置拦截器在日志中统一注入唯一的 `requestId`，保证注册、登录、画像、拦截日志具备全链路可追踪性。
- **Testcontainers**：引入 Testcontainers MySQL 容器，编写真实 MySQL 环境下的 Flyway schema 加载与 MyBatis Mapper 集成测试，消除 H2 和 MySQL 的方言差异。
- **Docker Compose**：编写本地一键启动 MySQL 服务的 Compose 描述文件。
- **GitHub Actions**：编写 `.github/workflows/ci.yml` 自动化 CI。
- **API 接口文档**：优先使用 Spring REST Docs 4.0 生成测试驱动接口文档；Swagger UI/OpenAPI 可作为后续增强，但必须选择明确支持 Boot 4 / Spring Framework 7 的 springdoc-openapi 版本。

---

## 2. 鉴权与令牌流转设计

### 2.1 双 Token 设计规约

- **Access JWT Token**:
  - **类型**：非对称签名或对称签名 JWT。
  - **有效期**：15 - 30 分钟。
  - **Payload**：`sub` (userId), `username`, `roles`, `exp` (过期时间), `iat` (签发时间)。
  - **校验**：无状态，Spring Security 会使用配置的密钥/公钥通过 Nimbus JWT 过滤器直接在内存中解码校验，无需查库。

- **Refresh Token**:
  - **类型**：使用 `SecureRandom` 生成的高熵随机字符串，建议至少 128 bit 熵，并用 Base64URL 编码返回客户端。
  - **有效期**：7 - 30 天。
  - **安全存放**：数据库只存储 `SHA-256` 后的 `token_hash`，防止数据库泄露导致 Refresh Token 裸奔。
  - **撤销机制**：支持主动撤销 (`revoked_at IS NOT NULL`)，支持过期拦截。

### 2.2 接口路由权限矩阵

| 接口端点 | 方法 | 鉴权方式 | 角色权限 | 备注 |
|---|---|---|---|---|
| `POST /api/auth/register` | POST | 匿名放行 | 任意 | 用户注册 |
| `POST /api/auth/login` | POST | 匿名放行 | 任意 | 用户登录，签发 Access & Refresh Token |
| `POST /api/auth/refresh` | POST | 匿名放行 | 任意 | 刷新 Access Token，支持 Refresh 滚动更新 |
| `POST /api/auth/logout` | POST | 匿名放行 | 任意 | 撤销传入的 Refresh Token |
| `GET /api/products/**` | GET | 匿名放行 | 任意 | 商城公开只读商品 API |
| `GET /api/users/me` | GET | Access JWT | `ROLE_USER` | 获取当前登录用户的基本账户信息 |
| `/api/me/**` | ALL | Access JWT | `ROLE_USER` | 获取或更新当前用户的画像、身体、偏好数据 |
| `/internal/**` | ALL | `X-Internal-Token` | 内部微服务/AI | 供 Python AI Agent 调用的接口，隔离外部访问 |

---

## 3. 数据库结构设计

 Flyway 将引入 `V3__user_auth_profile_schema.sql` 结构，对原来的 `user_body_profile` 命名优化为 `user_body_data`，`user_style_preference` 优化为 `user_preferences`。

### 3.1 账号与角色表

```sql
CREATE TABLE user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_account_username (username),
    UNIQUE KEY uk_user_account_phone (phone),
    UNIQUE KEY uk_user_account_email (email),
    KEY idx_user_account_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_role_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始化默认系统角色
INSERT INTO role (id, code, name) VALUES 
(1, 'USER', '普通用户'), 
(2, 'ADMIN', '管理员');
```

### 3.2 双 Token 控制与审计表 (`refresh_token` & `login_log`)

```sql
CREATE TABLE refresh_token (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    device_id VARCHAR(128) NULL,
    user_agent VARCHAR(512) NULL,
    ip_address VARCHAR(64) NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    KEY idx_refresh_token_user (user_id),
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE login_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NULL,
    username VARCHAR(64) NOT NULL,
    success TINYINT(1) NOT NULL,
    fail_reason VARCHAR(255) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_login_log_username (username),
    KEY idx_login_log_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 3.3 用户基础资料、身体测量数据、偏好设置表

```sql
CREATE TABLE user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(64) NULL,
    avatar_url VARCHAR(512) NULL,
    gender VARCHAR(32) NULL,
    birthday DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_profile_user (user_id),
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 身体测量数据，支持更精细的尺码预测
CREATE TABLE user_body_data (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    height_cm DECIMAL(5,2) NULL,
    weight_kg DECIMAL(5,2) NULL,
    gender VARCHAR(32) NULL,
    shoulder_width_cm DECIMAL(5,2) NULL,
    bust_cm DECIMAL(5,2) NULL,
    waist_cm DECIMAL(5,2) NULL,
    hip_cm DECIMAL(5,2) NULL,
    preferred_fit VARCHAR(32) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_body_data_user (user_id),
    CONSTRAINT fk_user_body_data_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 穿衣偏好，多值列表存为文本 JSON
CREATE TABLE user_preferences (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    preferred_styles TEXT NULL,      -- JSON Array: ["commute", "minimal"]
    preferred_colors TEXT NULL,      -- JSON Array: ["black", "navy"]
    disliked_colors TEXT NULL,       -- JSON Array: ["orange"]
    preferred_categories TEXT NULL,  -- JSON Array: ["外套", "长裤", "T恤"]
    budget_min DECIMAL(10,2) NULL,
    budget_max DECIMAL(10,2) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_preferences_user (user_id),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES user_account(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 4. API 接口契约设计

### 4.1 用户注册 `POST /api/auth/register`
- **Content-Type**: `application/json`
- **Request Body**:
```json
{
  "username": "alex_green",
  "password": "StrongPassword123!",
  "phone": "13912345678",
  "email": "alex@example.com"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "username": "alex_green",
    "status": "active"
  },
  "errorCode": null,
  "message": "ok"
}
```

### 4.2 用户登录 `POST /api/auth/login`
- **Request Body**:
```json
{
  "username": "alex_green",
  "password": "StrongPassword123!"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJhbGV4X2dyZWVuIiwicm9sZXMiOlsiUk9MRV9VU0VSIl19...",
    "refreshToken": "4a7b9c1d-8f2e-6a5b-3c4d-9e8f7a6b5c4d",
    "tokenType": "Bearer",
    "expiresIn": 1800
  },
  "errorCode": null,
  "message": "ok"
}
```

### 4.3 刷新 Token `POST /api/auth/refresh`
- **Request Body**:
```json
{
  "refreshToken": "4a7b9c1d-8f2e-6a5b-3c4d-9e8f7a6b5c4d"
}
```
- **Response (200 OK)**: 返回全新的双 Token，以实现 Refresh Token 的无感滚动刷新。
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJI...",
    "refreshToken": "9f8e7d6c-5b4a-3f2e-1d0c-9b8a7f6e5d4c",
    "tokenType": "Bearer",
    "expiresIn": 1800
  },
  "errorCode": null,
  "message": "ok"
}
```

### 4.4 退出登录 `POST /api/auth/logout`
- **Request Body**:
```json
{
  "refreshToken": "9f8e7d6c-5b4a-3f2e-1d0c-9b8a7f6e5d4c"
}
```
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": null,
  "errorCode": null,
  "message": "logout success"
}
```

### 4.5 获取当前登录用户 `GET /api/users/me`
- **Headers**: `Authorization: Bearer <accessToken>`
- **Response (200 OK)**:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "username": "alex_green",
    "roles": ["ROLE_USER"]
  },
  "errorCode": null,
  "message": "ok"
}
```

### 4.6 身体测量数据查询与更新 `GET/PUT /api/me/body-data`
- **Headers**: `Authorization: Bearer <accessToken>`
- **PUT Request Body**:
```json
{
  "heightCm": 178.50,
  "weightKg": 72.00,
  "gender": "male",
  "shoulderWidthCm": 46.00,
  "bustCm": 96.00,
  "waistCm": 80.00,
  "hipCm": 98.00,
  "preferredFit": "regular"
}
```
- **Response (200 OK)**: 返回最新更新的完整身体数据视图。

### 4.7 穿衣搭配偏好查询与更新 `GET/PUT /api/me/preferences`
- **Headers**: `Authorization: Bearer <accessToken>`
- **PUT Request Body**:
```json
{
  "preferredStyles": ["commute", "minimal"],
  "preferredColors": ["black", "navy", "white"],
  "dislikedColors": ["orange", "pink"],
  "preferredCategories": ["外套", "长裤", "T恤"],
  "budgetMin": 150.00,
  "budgetMax": 600.00
}
```
- **Response (200 OK)**: 返回保存的 JSON 偏好属性。

---

## 5. 文件与结构设计

新增及调整文件结构：

```text
pom.xml
docker-compose.yml
.github/workflows/ci.yml

src/main/resources/application.properties
src/test/resources/application-test.properties

src/main/resources/db/migration/V3__user_auth_profile_schema.sql

# 鉴权与过滤组件
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/JwtProperties.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityConfig.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/CurrentUser.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/security/CurrentUserMethodArgumentResolver.java

# 认证相关核心逻辑
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/api/AuthController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/RegisterRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/RegisterResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/LoginRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/LoginResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/RefreshRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/dto/LogoutRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/service/AuthService.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/service/JwtService.java # JWT 签发

# 用户与画像领域
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/api/UserController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/api/MeProfileController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserMeResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserProfileRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserProfileResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserBodyDataRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserBodyDataResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserPreferencesRequest.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/dto/UserPreferencesResponse.java

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/UserAccount.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/UserProfile.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/UserBodyData.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/UserPreferences.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/RefreshToken.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/model/LoginLog.java

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/mapper/UserMapper.java
src/main/resources/mapper/user/UserMapper.xml
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/service/UserProfileService.java

# 链路拦截器
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/MdcInterceptor.java

# 测试套件
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/BaseIntegrationTest.java # 集成测试基类，拉起 Testcontainers MySQL
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/auth/AuthControllerTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/user/UserMapperTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/user/UserProfileControllerTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/security/SecurityTests.java
```

---

## 6. 任务拆分与执行清单

### 6.1 Phase 1A: 用户注册登录与双 Token 核心机制

#### Task 1: 引入 Spring Security 7 及双 Token 依赖配置
- [ ] **Step 1**: 在 `pom.xml` 中引入 `spring-boot-starter-security` 和 `spring-boot-starter-oauth2-resource-server` 依赖。确保支持 Nimbus JWT。
- [ ] **Step 2**: 在 `application.properties` 和 `application-test.properties` 中添加对 JWT 签名所需的对称密钥（支持 HMAC-SHA256，密钥字符串必须至少 256 位，即 32 字节）及有效期限配置：
  ```properties
  app.jwt.secret=9a8b7c6d5e4f3g2h1i0j9k8l7m6n5o4p3q2r1s0t9u8v7w6x5y4z321012345678
  app.jwt.access-expiration-seconds=1800
  app.jwt.refresh-expiration-seconds=2592000
  ```
- [ ] **Step 3**: 创建强类型配置 Record `JwtProperties.java` 映射上述配置属性。
- [ ] **Step 4**: 运行 `mvn clean compile` 验证依赖引入未引起编译错误。

#### Task 2: 创建 Flyway 迁移脚本（V3 数据库演进）
- [ ] **Step 1**: 在 `src/main/resources/db/migration/` 中创建 `V3__user_auth_profile_schema.sql`，写入前述第 3 节中设计的 7 张数据表的建表语句（添加外键约束及级联删除，设置字段的 utf8mb4 字符集）。
- [ ] **Step 2**: 初始化系统基本角色 `USER` 和 `ADMIN` 到 `role` 表中。
- [ ] **Step 3**: 启动你的本地开发 MySQL，通过 Flyway 自动迁移完成表结构的落地验证。

#### Task 3: 落地模型层与 MyBatis UserMapper
- [ ] **Step 1**: 创建 `UserAccount.java`、`UserProfile.java`、`UserBodyData.java`、`UserPreferences.java`、`RefreshToken.java`、`LoginLog.java` 六个核心数据模型实体类。
- [ ] **Step 2**: 编写 `UserMapper.java` 接口，定义基本 CRUD 方法：
  - 用户查询：`findByUsername`、`findById`。
  - 用户检测：`countByUsername`、`countByPhone`、`countByEmail`。
  - 角色操作：`findRoleIdByCode`、`insertUserRole`、`findRoleCodesByUserId`。
  - 刷新令牌：`insertRefreshToken`、`findRefreshTokenByHash`、`updateRefreshTokenRevocation`、`updateRefreshTokenLastUsed`。
  - 审计日志：`insertLoginLog`。
  - 画像 upsert 逻辑：`upsertProfile`、`upsertBodyData`、`upsertPreferences`。
- [ ] **Step 3**: 编写 `UserMapper.xml` 映射 SQL。对于偏好表中的多值列表（如 `preferred_styles` 等），统一在 XML 中将其映射为普通 TEXT。在 Java 侧利用 Jackson 序列化/反序列化。
- [ ] **Step 4**: 在 `UserMapperTests.java` 中编写验证：测试正常插入用户、插入并校验刷新令牌状态。

#### Task 4: 实现有状态双 Token 控制服务与密码单向 Hash
- [ ] **Step 1**: 编写 `JwtService.java` 类，提供在内存中创建 Access JWT Token 的方法。使用 Spring Security OAuth2 JOSE / Nimbus 进行对称 HMAC 签名，塞入 Claim 字段。
- [ ] **Step 2**: 在 `AuthService.java` 中利用 `BCryptPasswordEncoder` 实现密码匹配校验与用户注册单向 Hash。
- [ ] **Step 3**: 实现登录与 Token 刷新逻辑：
  - **登录时**：除生成短效 JWT 之外，利用 `SecureRandom` 生成一个一次性的长效 `refreshToken` 随机字符串。计算其 SHA-256 Hash 并存入数据库 `refresh_token` 表，将原始明文 Token 返回给客户端。
  - **刷新时**：客户端上传明文 `refreshToken`。Service 侧对其做 SHA-256 哈希去库中查询匹配，确认未过期、未被撤销（`revoked_at IS NULL`）。验证成功后废弃该 Token 并重新滚动生成一套全新的双 Token。
  - **退出时**：标记对应的 `refreshToken` 为 `revoked_at = NOW()`。
- [ ] **Step 4**: 实现 `login_log` 审计功能：在登录成功/失败的分支处，记录一条对应的登录日志至数据库。

#### Task 5: 实现 Spring Security OAuth2 鉴权与网关隔离
- [ ] **Step 1**: 编写 `CurrentUser.java` Record 用于承载当前的登录上下文信息。
- [ ] **Step 2**: 编写 `SecurityConfig.java` 类：
  - 注册 `BCryptPasswordEncoder` bean。
  - 配置基于 `NimbusJwtDecoder` 的对称密钥 JWT 解码器 bean：
    ```java
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        SecretKey secretKey = new SecretKeySpec(properties.secret().getBytes(), "HMACSHA256");
        return NimbusJwtDecoder.withSecretKey(secretKey).build();
    }
    ```
  - 配置 `SecurityFilterChain` 进行接口权限细粒度划分，允许匿名访问 `/api/auth/**`、`/api/products/**`，但对 `/api/me/**`、`/api/users/me` 强制校验 Bearer 令牌，并隔离已有 `/internal/**` 的拦截配置。
- [ ] **Step 3**: 编写 `CurrentUserMethodArgumentResolver.java` 注册到 MVC 配置中，以便在 Controller 接口的参数中可以通过 `@AuthenticationPrincipal` 或直接绑定当前登录信息。

#### Task 6: 编写注册、登录与双 Token 控制 Controller 与端点测试
- [ ] **Step 1**: 创建 `AuthController.java`，暴露：
  - `POST /api/auth/register` (DTO 参数 `@Valid` 校验)
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
- [ ] **Step 2**: 编写 DTO 接收类：`RegisterRequest.java`、`LoginRequest.java`、`RefreshRequest.java`、`LogoutRequest.java`。
- [ ] **Step 3**: 编写单元/接口集成测试 `AuthControllerTests.java`：通过 MockMvc 模拟注册、登录、携带 Token 获取自我信息、利用 RefreshToken 重新刷新 Access 令牌、退出后请求失效的闭环流程。验证 `login_log` 成功在库里打上记录。

---

### 6.2 Phase 1B: 用户画像与细粒度偏好模块

#### Task 7: 建立画像 DTO 规约与多值字段序列化机制
- [ ] **Step 1**: 编写画像模型 DTO 类：
  - `UserProfileRequest.java` / `UserProfileResponse.java`（字段：nickname, avatarUrl, gender, birthday）
  - `UserBodyDataRequest.java` / `UserBodyDataResponse.java`（字段：heightCm, weightKg, gender, shoulderWidthCm, bustCm, waistCm, hipCm, preferredFit）
  - `UserPreferencesRequest.java` / `UserPreferencesResponse.java`（字段：preferredStyles, preferredColors, dislikedColors, preferredCategories, budgetMin, budgetMax，其中多值使用 `List<String>`）
- [ ] **Step 2**: 在 DTO 属性上增加验证注解：
  - 身高、体重必须大于 0：`@DecimalMin(value = "0.1", message = "must be positive")`
  - 尺码偏好限制：`@Pattern(regexp = "^(loose|regular|slim)$", message = "must be loose, regular or slim")`
  - 预算限制：`@DecimalMin(value = "0.0")`，且在 Service 校验 `budgetMin <= budgetMax`。

#### Task 8: 编写画像服务逻辑与 MeProfileController 控制器
- [ ] **Step 1**: 创建 `UserProfileService.java` 服务，实现 `UserProfile`、`UserBodyData` 和 `UserPreferences` 的单表 upsert 查询和更新。
- [ ] **Step 2**: 当更新穿衣偏好时，在 Service 侧利用 Jackson 序列化工具 `ObjectMapper` 将 Java DTO 中的列表转换成 JSON String 并持久化到 `user_preferences` 表的 TEXT 属性中；读取时反向转换回 `List<String>` 并输出。
- [ ] **Step 3**: 编写 `MeProfileController.java`，路由前缀为 `/api/me`，暴露出 GET/PUT 接口端点，通过注入 `@AuthenticationPrincipal CurrentUser` 绑定操作人主键，禁止任何越权横向更新。
- [ ] **Step 4**: 编写接口测试 `UserProfileControllerTests.java`，断言校验：
  - 未携带 Bearer 令牌时直接拒绝请求并返回 401。
  - 身高/体重输入非法数值时触发校验拦截，返回标准 400 BadRequest 错误。
  - 预算上限低于下限时触发业务校验异常。
  - 能够成功保存并再次查出复杂穿衣偏好 JSON 数组。

---

### 6.3 Phase 1C: 工程化与测试强化能力 (简历级加分点)

#### Task 9: 日志链路 requestId MDC 跟踪
- [ ] **Step 1**: 编写并注册 `MdcInterceptor.java` 拦截器。在 `preHandle` 阶段，通过 UUID 生成统一的 `requestId`，塞入 SLF4J 的 `MDC` 上下文中；在 `afterCompletion` 阶段执行 `MDC.clear()` 防止内存泄露。
- [ ] **Step 2**: 调整本地 `logback.xml`（或 `logback-spring.xml`），将 `[reqId=%X{requestId}]` 字段融入日志 Pattern 样式中，保证系统内业务、拦截、异常日志均附带追踪主键。

#### Task 10: 编写 Testcontainers 数据库容器集成测试
- [ ] **Step 1**: 在 `pom.xml` 中引入 `org.testcontainers:mysql` 及 `org.testcontainers:junit-jupiter` 测试域依赖。
- [ ] **Step 2**: 编写 `BaseIntegrationTest.java` 作为所有集成测试类的基类：
  ```java
  @ActiveProfiles("test")
  @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
  @Testcontainers
  public abstract class BaseIntegrationTest {
      @Container
      static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
              .withDatabaseName("intelligent_outfit")
              .withUsername("test")
              .withPassword("test");

      @DynamicPropertySource
      static void registerProperties(DynamicPropertyRegistry registry) {
          registry.add("spring.datasource.url", mysql::getJdbcUrl);
          registry.add("spring.datasource.username", mysql::getUsername);
          registry.add("spring.datasource.password", mysql::getPassword);
      }
  }
  ```
- [ ] **Step 3**: 修改 `UserMapperTests` 和关键 `AuthControllerTests`，让其继承 `BaseIntegrationTest`。在真实 MySQL 临时容器下进行 Flyway 脚本编译、MyBatis 动态 SQL 查询功能测试，规避 H2 数据库不支持 `JSON` 或其他高级 MySQL 函数方言的痛点。

#### Task 11: 本地一键 Docker Compose 环境与自动 CI
- [ ] **Step 1**: 在项目根目录下，新建 `docker-compose.yml` 描述文件：
  ```yaml
  services:
    db:
      image: mysql:8.0
      container_name: io_mysql
      ports:
        - "3307:3306"
      environment:
        MYSQL_DATABASE: intelligent_outfit
        MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-change-me}
      volumes:
        - io_mysql_data:/var/lib/mysql
  volumes:
    io_mysql_data:
  ```
- [ ] **Step 2**: 新建 `.github/workflows/ci.yml` 配置文件，定义拉取代码、加载 Java 21 环境、启动数据库临时容器并运行 `mvn test` 的完整 GitHub Actions 流水线逻辑。

#### Task 12: 集成 API 接口文档生成
- [ ] **Step 1**: 引入 Spring REST Docs 4.0 相关测试依赖，优先通过 MockMvc 测试生成接口片段，保证接口文档和自动化测试一致。
- [ ] **Step 2**: 在 `AuthControllerTests` 和 `UserProfileControllerTests` 中补充 REST Docs 断言，覆盖 `/api/auth/**`、`/api/users/me`、`/api/me/**` 的请求字段、响应字段和鉴权头。
- [ ] **Step 3**: 输出本阶段接口文档到 `target/generated-snippets`，并在 `docs/api-testing-with-reqable.md` 中引用关键请求/响应示例。
- [ ] **Step 4**: 如果后续需要 Swagger UI，再单独确认并锁定支持 Spring Boot 4 / Spring Framework 7 的 springdoc-openapi 版本，不在本阶段强依赖不确定版本。

---

## 7. 验收测试指引与标准

### 7.1 Reqable 环境变量配置
```text
base_url = http://127.0.0.1:8080
X-Internal-Token = dev-internal-token
Authorization = Bearer {{access_token}} （登录后手动/脚本替换到临时环境上下文）
```

### 7.2 注册新用户
- **端点**：`POST {{base_url}}/api/auth/register`
- **Body**:
```json
{
  "username": "tester",
  "password": "Password99!",
  "phone": "13900001111",
  "email": "tester@recommend.com"
}
```
- **期待输出**：200 状态码，返回包含 `userId` 和 `username: tester` 的 JSON。

### 7.3 登录并获取双 Token
- **端点**：`POST {{base_url}}/api/auth/login`
- **Body**:
```json
{
  "username": "tester",
  "password": "Password99!"
}
```
- **期待输出**：返回中包含 `accessToken` (JWT), `refreshToken` (高熵随机字符串), `tokenType: Bearer`。复制该 accessToken 填入 Authorization Header。同时验证数据库 `login_log` 成功记录一次 `success=1` 的日志。

### 7.4 验证鉴权边界隔离
- **端点**：`GET {{base_url}}/api/users/me`
- **情况 A：不带 Bearer Token 发送请求** $\rightarrow$ 返回 **401 Unauthorized**。
- **情况 B：携带错误或过期 Bearer Token** $\rightarrow$ 返回 **401 Unauthorized**。
- **情况 C：携带正确 Bearer Token** $\rightarrow$ 返回 200 OK 并列出 `userId: X` 且其角色列表内包含 `ROLE_USER`。
- **情况 D：携带 Bearer Token 请求内部接口 `/internal/inventory?skuId=2001`** $\rightarrow$ 返回 **401 Unauthorized**。必须使用 `X-Internal-Token` Header 才能调通。

### 7.5 保存并再次读取身体和偏好数据
- **端点**：`PUT {{base_url}}/api/me/body-data`（更新）
- **期待输出**：身高体重等信息保存入库。再次调用 `GET {{base_url}}/api/me/body-data` 能够读取出刚更新的信息。
- **端点**：`PUT {{base_url}}/api/me/preferences`（更新风格偏好）
- **期待输出**：支持向 `preferredStyles` 属性传递 JSON 数组 `["commute", "minimal"]`，数据库中能正确记录 JSON 数据。再次 `GET` 时，数据以列表形式正确反序列化呈现。

### 7.6 令牌滚动刷新与撤销测试
- **端点**：`POST {{base_url}}/api/auth/refresh`
- **Body**: 传入之前登录时拿到的 `refreshToken`。
- **期待输出**：返回全新的双 Token，原有的 `refreshToken` 标记过期或被撤销，从而实现了 Token 的滚动更新。
- **端点**：`POST {{base_url}}/api/auth/logout`
- **期待输出**：再次以原有 RefreshToken 请求刷新，将返回 **401/400 Token Invalid** 错误，证明令牌已彻底从数据库哈希表层面撤销。

---

## 8. 质量保障与安全审计 (简历卖点描述)

1. **防范密码明文泄露**：
   - 数据库任何表决不能有密码的明文存根，统一使用 `BCrypt` 进行单向随机盐值加密。
2. **防范 Refresh Token 数据库沦陷攻击**：
   - `refresh_token` 表采用类似密码存储机制，仅存储 SHA-256 后的摘要值（`token_hash`）。即便数据库被拖库，黑客也无法伪造客户端持有明文的 RefreshToken 请求刷新，防患于未然。
3. **防范横向越权漏洞**：
   - 个人画像的所有接口端点统一绑定至 Spring Security 的核心鉴权上下文 `CurrentUser.userId`。业务方法内不从 Request Param 中读取主键 ID 进行查询/更新，杜绝了攻击者修改参数就能纵向修改他人身体/风格偏好的横向越权问题。
4. **日志追踪防范敏感泄漏**：
   - MDC 中的 `requestId` 实现了对复杂推荐链条和鉴权过滤的秒级追踪。同时，操作日志与登录日志中对敏感信息进行混淆或忽略（如密码不记录入日志文件），保证安全性合规。
