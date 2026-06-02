# 后端功能对照表

本文档用于梳理 Intelligent Outfit Recommendation System 当前 Java 后端已经实现的功能、对应代码位置、数据库表、API 接口和测试用例。

当前阶段重点是：商品库、SKU、库存、服装细粒度属性、用户认证与画像、会话记录、Java 调 Python AI 服务的第一版同步链路，以及购物车 Cart MVP。购物车开发文档见 `docs/superpowers/plans/2026-06-01-cart-mvp.md`。下一阶段订单 MVP 开发文档见 `docs/superpowers/plans/2026-06-02-order-mvp.md`。本文档也作为后续继续开发订单、支付、SSE 和 MQ 的功能追踪表。

## 当前阶段范围

## 技术栈基线

当前后端技术栈按 Spring Boot 4 路线维护：

| 方向 | 当前选择 | 说明 |
|---|---|---|
| Java | Java 21 | 与 Spring Boot 4.0.6 兼容 |
| Web 框架 | Spring Boot 4.0.6 / Spring Web MVC | Boot 4 基于 Spring Framework 7，第三方依赖需确认兼容性 |
| 数据库 | MySQL 8.0 | 本地开发主库 |
| 数据迁移 | Flyway | 管理表结构版本 |
| 数据访问 | MyBatis Spring Boot Starter 4.0.0 + XML | 保持 SQL 可控，贴合当前项目重构方向 |
| 参数校验 | spring-boot-starter-validation / `@Validated` | DTO 入参校验 |
| 测试 | JUnit 5、MockMvc、H2 | 当前已有自动化测试基础 |
| 推荐测试升级 | Testcontainers + MySQL 1.21.4 | CI 可用真实 MySQL 8 验证 Flyway 迁移，减少 H2/MySQL 方言差异 |
| 本地依赖 | Docker Compose | 已提供 MySQL 一键启动 |
| CI | GitHub Actions | 已配置 Maven 自动测试，CI 中开启 MySQL 容器测试 |
| 接口文档 | Spring REST Docs + MockMvc | 已从 auth 接口生成契约片段，后续继续覆盖 user/conversation/assistant |
| 手动测试 | Reqable | 注册、登录、商品、库存、internal API 验证 |

### 已实现

- 商品目录基础能力
- SKU 查询能力
- 库存查询能力
- 服装推荐所需细粒度属性：材质、版型、季节、风格标签
- 商品推荐候选查询
- Python AI 服务可调用的 internal API
- 公开商品只读 API
- 基于 Flyway 的数据库初始化
- H2 测试环境和 MySQL 8.0 本地运行环境
- 用户注册登录
- Spring Security + Access JWT 用户接口鉴权
- Refresh Token 数据库存根、滚动刷新和登出撤销
- 登录日志审计
- 当前用户信息接口
- 用户基础资料、身体数据和穿衣偏好接口
- 用户认证与画像 MyBatis XML 数据访问层
- Docker Compose 本地 MySQL 环境
- GitHub Actions 自动测试
- MDC requestId 日志链路追踪
- Testcontainers + MySQL Flyway 迁移测试
- Spring REST Docs auth 接口契约片段
- 会话记录：创建会话、查询会话列表、查询消息历史、归档会话
- Java assistant-service 同步调用 Python AI 服务并保存 user/assistant 消息
- 购物车：当前登录用户加购 SKU、查询购物车、修改数量、删除单项、清空购物车

### 未实现

- 订单（下一阶段计划中，见 `docs/superpowers/plans/2026-06-02-order-mvp.md`）
- 支付
- SSE / WebSocket 流式返回
- MQ 异步推荐任务

## 功能总览

| 业务能力 | 当前状态 | Java 模块 | 数据库表 | API | 测试用例 |
|---|---|---|---|---|---|
| 商品搜索 | 已实现 | product | product_spu、product_sku、category | GET /api/products、GET /internal/products/search | ProductCatalogMapperTests、ProductControllerTests、InternalProductControllerTests |
| 商品详情 | 已实现 | product | product_spu、product_material、product_season、product_style_tag、product_attribute | GET /api/products/{spuId}、GET /internal/products/{spuId} | ProductControllerTests、InternalProductControllerTests |
| SKU 查询 | 已实现 | product | product_sku、color、size_option | GET /internal/skus/search | ProductCatalogMapperTests、InternalProductControllerTests |
| 库存查询 | 已实现 | inventory | inventory、product_sku | GET /internal/inventory | InventoryMapperTests、InternalInventoryControllerTests |
| 推荐候选商品查询 | 已实现 | product | product_spu、product_sku、inventory、material、season、style_tag、fit_type | GET /api/products/recommendation-candidates、GET /internal/recommendation-candidates | ProductCatalogMapperTests、ProductControllerTests、InternalProductControllerTests |
| Internal 鉴权 | 已实现 | common/internal | 无 | /internal/** | InternalProductControllerTests |
| 用户注册登录 | 已实现 | auth | user_account、role、user_role、login_log | POST /api/auth/register、POST /api/auth/login | AuthControllerTests、UserAuthMapperTests |
| Access/Refresh Token | 已实现 | auth、security | refresh_token | POST /api/auth/refresh、POST /api/auth/logout、GET /api/users/me | AuthControllerTests |
| 用户基础资料 | 已实现 | user | user_profile | GET/PUT /api/me/profile | UserProfileControllerTests、UserProfileMapperTests |
| 身体数据 | 已实现 | user | user_body_data | GET/PUT /api/me/body-data | UserProfileControllerTests、UserProfileMapperTests |
| 穿衣偏好 | 已实现 | user | user_preferences | GET/PUT /api/me/preferences | UserProfileControllerTests、UserProfileMapperTests |
| 会话记录 | 已实现 | conversation | chat_session、chat_message | POST/GET/DELETE /api/conversations、GET /api/conversations/{threadId}/messages | ConversationMapperTests、ConversationControllerTests |
| AI 同步问答 | 已实现 | assistant | chat_session、chat_message、product_*、user_* | POST /api/assistant/chat | AssistantServiceTests、AssistantControllerTests |
| 购物车 | 已实现 | cart | cart_item | GET/POST/PUT/DELETE /api/cart/items | CartMapperTests、CartServiceTests、CartControllerTests |
| 订单 | 计划中 | order | sales_order、order_item | POST/GET /api/orders、GET /api/orders/{orderNo} | OrderMapperTests、OrderServiceTests、OrderControllerTests |
| requestId 日志追踪 | 已实现 | common/logging | 无 | 所有 HTTP 请求响应头 `X-Request-Id` | MdcRequestIdInterceptorTests |
| MySQL 容器迁移测试 | 已实现 | support test | flyway_schema_history、全部业务表 | 无 | MySqlFlywayMigrationTests |
| 统一响应格式 | 已实现 | common/api | 无 | 所有 Controller | Controller 测试覆盖 |

## 模块与代码位置

### common

负责统一响应、异常处理和 internal 接口鉴权。

| 文件 | 作用 |
|---|---|
| `common/api/ApiResponse.java` | 统一 API 返回格式 |
| `common/api/ErrorResponse.java` | 错误响应结构 |
| `common/error/BadRequestException.java` | 参数错误异常 |
| `common/error/ResourceNotFoundException.java` | 资源不存在异常 |
| `common/error/ExternalServiceException.java` | 外部服务调用失败异常，当前用于 Python AI 服务 |
| `common/error/GlobalExceptionHandler.java` | 统一异常处理 |
| `common/internal/InternalApiProperties.java` | internal token 配置 |
| `common/internal/InternalApiInterceptor.java` | 校验 `X-Internal-Token` |
| `common/internal/WebMvcConfig.java` | 注册 requestId 和 `/internal/**` 拦截器 |
| `common/logging/MdcRequestIdInterceptor.java` | 为每个 HTTP 请求注入/回传 `X-Request-Id`，并写入 SLF4J MDC |

### security

负责普通用户 API 的 Spring Security 鉴权、JWT 编解码和当前登录用户解析。

| 文件 | 作用 |
|---|---|
| `security/SecurityConfig.java` | 配置匿名接口、受保护接口、JWT Resource Server、BCrypt |
| `security/JwtProperties.java` | 读取 `app.jwt.*` 配置 |
| `security/JwtService.java` | 签发 Access JWT |
| `security/CurrentUser.java` | 从 Spring Security Authentication 中解析当前用户 |

### auth

负责注册、登录、双 Token、登出和当前用户账户信息。

| 文件 | 作用 |
|---|---|
| `auth/api/AuthController.java` | `/api/auth/**` 注册、登录、刷新、登出接口 |
| `auth/api/CurrentUserController.java` | `/api/users/me` 当前用户接口 |
| `auth/service/AuthService.java` | 账号唯一性、密码校验、Token 签发、Refresh Token 撤销、登录日志 |
| `auth/mapper/UserAuthMapper.java` | 用户认证 MyBatis Mapper 接口 |
| `src/main/resources/mapper/auth/UserAuthMapper.xml` | 用户认证 SQL 映射 |
| `auth/model/UserAccount.java` | 登录账号模型 |
| `auth/model/RefreshTokenRecord.java` | Refresh Token 数据库存根模型 |
| `auth/model/LoginLog.java` | 登录日志模型 |

### user

负责为 AI 推荐提供结构化用户上下文，包括基础资料、身体数据和穿衣偏好。

| 文件 | 作用 |
|---|---|
| `user/api/UserProfileController.java` | `/api/me/**` 当前用户画像接口 |
| `user/service/UserProfileService.java` | 画像保存、读取、偏好 JSON 列表转换和预算校验 |
| `user/mapper/UserProfileMapper.java` | 用户画像 MyBatis Mapper 接口 |
| `src/main/resources/mapper/user/UserProfileMapper.xml` | 用户画像 SQL 映射 |
| `user/model/UserProfile.java` | 用户基础资料模型 |
| `user/model/UserBodyData.java` | 身体数据模型 |
| `user/model/UserPreferences.java` | 穿衣偏好模型 |

### product

负责商品、SKU、商品详情和推荐候选商品查询。

| 文件 | 作用 |
|---|---|
| `product/api/ProductController.java` | 公开商品只读接口 |
| `product/api/InternalProductController.java` | Python AI internal 商品接口 |
| `product/service/ProductCatalogService.java` | 商品业务逻辑和参数校验 |
| `product/mapper/ProductMapper.java` | 商品 MyBatis Mapper 接口 |
| `src/main/resources/mapper/product/ProductMapper.xml` | 商品 SQL 映射 |
| `product/dto/RecommendationCandidateQuery.java` | 推荐候选商品查询参数 |
| `product/model/ProductSearchItem.java` | 商品搜索返回模型 |
| `product/model/ProductDetail.java` | 商品详情返回模型 |
| `product/model/SkuSearchItem.java` | SKU 查询返回模型 |
| `product/model/RecommendationCandidate.java` | 推荐候选商品返回模型 |
| `product/model/ProductAttributeItem.java` | 商品扩展属性查询中间模型 |

### inventory

负责 SKU 库存查询。

| 文件 | 作用 |
|---|---|
| `inventory/api/InternalInventoryController.java` | Python AI internal 库存接口 |
| `inventory/service/InventoryQueryService.java` | 库存业务逻辑和参数校验 |
| `inventory/mapper/InventoryMapper.java` | 库存 MyBatis Mapper 接口 |
| `src/main/resources/mapper/inventory/InventoryMapper.xml` | 库存 SQL 映射 |
| `inventory/model/InventoryView.java` | 库存返回模型 |

### cart

负责当前登录用户的购物车意图数据，不锁库存、不生成价格快照，也不向 Python AI 服务暴露 internal API。

| 文件 | 作用 |
|---|---|
| `cart/api/CartController.java` | `/api/cart/items` 当前用户购物车接口 |
| `cart/service/CartService.java` | 加购、列表、改数量、删除和清空的用户隔离业务逻辑 |
| `cart/mapper/CartMapper.java` | 购物车 MyBatis Mapper 接口 |
| `src/main/resources/mapper/cart/CartMapper.xml` | 购物车 SQL 映射 |
| `cart/model/CartItem.java` | `cart_item` 持久化模型 |
| `cart/model/CartItemView.java` | 购物车列表展示视图 |
| `cart/dto/AddCartItemRequest.java` | 加购请求 DTO |
| `cart/dto/UpdateCartItemRequest.java` | 修改数量请求 DTO |

### conversation

负责当前用户的 AI 会话和消息历史，所有查询都绑定 JWT 中的 `userId`。

| 文件 | 作用 |
|---|---|
| `conversation/api/ConversationController.java` | `/api/conversations/**` 会话接口 |
| `conversation/service/ConversationService.java` | 会话创建、列表、消息查询、归档、消息追加 |
| `conversation/mapper/ConversationMapper.java` | 会话 MyBatis Mapper 接口 |
| `src/main/resources/mapper/conversation/ConversationMapper.xml` | 会话 SQL 映射 |
| `conversation/model/ChatSession.java` | 会话表模型 |
| `conversation/model/ChatMessage.java` | 消息表模型 |
| `conversation/dto/*` | 会话和消息响应 DTO |

### assistant

负责 Java 调 Python AI 服务。Java 端组装用户画像、会话历史、商品候选池，再调用 Python `/chat`，并把 user/assistant 消息写回 conversation 模块。

| 文件 | 作用 |
|---|---|
| `assistant/api/AssistantController.java` | `/api/assistant/chat` 同步问答接口 |
| `assistant/service/AssistantService.java` | 创建/复用 thread、保存消息、调用 Python、返回结果 |
| `assistant/service/AssistantContextService.java` | 组装用户画像、会话历史和推荐候选商品 |
| `assistant/client/PythonAssistantClient.java` | Python AI 客户端接口，便于测试替换 |
| `assistant/client/RestPythonAssistantClient.java` | 基于 Java HttpClient 的 Python `/chat` HTTP 实现 |
| `assistant/dto/*` | Java 前端请求/响应和 Java 调 Python 请求/响应 DTO |

### 数据访问层

当前商品和库存模块已经从 `NamedParameterJdbcTemplate` Repository 重构为 MyBatis：

- Mapper Interface 定义数据库访问方法。
- Mapper XML 保存 SQL。
- Service 层负责参数校验和复杂字段装配，例如商品详情里的材质、季节、风格标签和扩展属性。

## 数据库表对照

| 表名 | 作用 |
|---|---|
| category | 商品分类，例如上衣、T恤、外套、下装、长裤 |
| product_spu | 商品 SPU，表示一个商品款式 |
| product_sku | 商品 SKU，表示具体颜色和尺码组合 |
| color | 颜色字典 |
| size_option | 尺码字典 |
| fit_type | 版型，例如合身、宽松、直筒 |
| season | 季节标签，例如 spring、summer、autumn、winter |
| style_tag | 风格标签，例如 commute、casual、minimal、sport |
| material | 材质，例如纯棉、聚酯纤维混纺 |
| product_material | 商品和材质关系 |
| product_season | 商品和季节关系 |
| product_style_tag | 商品和风格关系 |
| product_attribute | 商品扩展属性 |
| product_image | 商品图片 |
| size_rule | 尺码规则 |
| inventory | SKU 库存 |
| user_account | 用户登录账号、手机号、邮箱、密码哈希和状态 |
| role | 系统角色字典 |
| user_role | 用户和角色关系 |
| refresh_token | Refresh Token 哈希存根、过期时间、撤销时间和设备信息 |
| login_log | 登录成功/失败审计日志 |
| user_profile | 用户昵称、头像、性别和生日 |
| user_body_data | 用户身高、体重、三围、肩宽和偏好版型 |
| user_preferences | 用户风格、颜色、品类偏好和预算区间 |
| chat_session | AI 会话，保存 thread_id、user_id、标题、状态和最后消息时间 |
| chat_message | AI 会话消息，保存 user/assistant 消息内容、角色、状态和 requestId |
| cart_item | 当前用户购物车条目，保存 user_id、sku_id 和购买意图数量 |
| flyway_schema_history | Flyway 数据库迁移记录 |

## API 对照

### 公开 API

公开 API 给商城前端或本地调试使用，不需要 internal token。

| API | 作用 | 是否需要 token |
|---|---|---|
| GET /api/products | 商品搜索 | 否 |
| GET /api/products/{spuId} | 商品详情 | 否 |
| GET /api/products/recommendation-candidates | 推荐候选商品查询 | 否 |

### 普通用户 API

普通用户 API 使用 `Authorization: Bearer <accessToken>` 鉴权。`accessToken` 由 `/api/auth/login` 或 `/api/auth/refresh` 返回。

| API | 作用 | 是否需要 Bearer Token |
|---|---|---|
| POST /api/auth/register | 用户注册 | 否 |
| POST /api/auth/login | 用户登录并签发 Access/Refresh Token | 否 |
| POST /api/auth/refresh | 使用 Refresh Token 滚动刷新双 Token | 否 |
| POST /api/auth/logout | 撤销 Refresh Token | 否 |
| GET /api/users/me | 获取当前登录用户 | 是 |
| GET /api/me/profile | 获取当前用户基础资料 | 是 |
| PUT /api/me/profile | 更新当前用户基础资料 | 是 |
| GET /api/me/body-data | 获取当前用户身体数据 | 是 |
| PUT /api/me/body-data | 更新当前用户身体数据 | 是 |
| GET /api/me/preferences | 获取当前用户穿衣偏好 | 是 |
| PUT /api/me/preferences | 更新当前用户穿衣偏好 | 是 |
| POST /api/conversations | 创建当前用户会话 | 是 |
| GET /api/conversations | 查询当前用户会话列表 | 是 |
| GET /api/conversations/{threadId}/messages | 查询当前用户某个会话的消息历史 | 是 |
| DELETE /api/conversations/{threadId} | 归档当前用户某个会话 | 是 |
| POST /api/assistant/chat | 同步调用 Python AI 导购服务并落库消息 | 是 |
| GET /api/cart/items | 查询当前用户购物车 | 是 |
| POST /api/cart/items | 添加 SKU 到当前用户购物车，同 SKU 合并数量 | 是 |
| PUT /api/cart/items/{skuId} | 修改当前用户购物车中某个 SKU 的数量 | 是 |
| DELETE /api/cart/items/{skuId} | 删除当前用户购物车中某个 SKU | 是 |
| DELETE /api/cart/items | 清空当前用户购物车 | 是 |

### Internal API

Internal API 给 Python AI 推荐服务调用，需要请求头：

```text
X-Internal-Token: dev-internal-token
```

| API | 作用 | Python 使用场景 |
|---|---|---|
| GET /internal/products/search | 搜索商品 | 用户问某类衣服时查商品事实 |
| GET /internal/products/{spuId} | 商品详情 | AI 解释推荐理由 |
| GET /internal/skus/search | 根据 SPU、颜色、尺码查 SKU | AI 确认具体可购买款式 |
| GET /internal/inventory | 查询 SKU 库存 | AI 避免推荐无库存商品 |
| GET /internal/recommendation-candidates | 查询推荐候选商品 | AI 根据风格、季节、材质、预算筛选商品 |

## 测试对照

| 测试文件 | 验证内容 |
|---|---|
| `ProductCatalogMapperTests.java` | 商品搜索、SKU 查询、商品详情基础字段、商品多值属性、推荐候选商品 SQL 是否正确 |
| `ProductCatalogServiceTests.java` | 商品业务参数校验和商品详情复杂字段装配是否正确 |
| `ProductControllerTests.java` | 公开商品 API 是否可用 |
| `InternalProductControllerTests.java` | internal 商品 API 和 token 鉴权是否正确 |
| `InventoryMapperTests.java` | 库存 SQL 查询是否正确 |
| `InventoryQueryServiceTests.java` | 库存业务参数校验是否正确 |
| `InternalInventoryControllerTests.java` | internal 库存 API 是否可用 |
| `IntelligentOutfitRecommendationSystemApplicationTests.java` | Spring Boot 上下文是否能启动 |
| `AuthControllerTests.java` | 用户注册、登录、Access JWT 鉴权、Refresh Token 刷新和登出撤销 |
| `UserAuthMapperTests.java` | 用户账号、角色、Refresh Token 和登录日志 SQL 映射 |
| `UserProfileControllerTests.java` | 当前用户基础资料、身体数据、穿衣偏好接口和参数校验 |
| `UserProfileMapperTests.java` | 用户画像三类表的 MyBatis SQL 映射 |
| `ConversationMapperTests.java` | 会话和消息 MyBatis SQL 映射、归档隔离 |
| `ConversationControllerTests.java` | 会话接口鉴权、当前用户隔离、归档后列表不可见 |
| `AssistantServiceTests.java` | assistant-service 创建会话、保存消息、调用 Python 客户端的业务顺序 |
| `AssistantControllerTests.java` | `/api/assistant/chat` 鉴权、AI 响应和消息落库 |
| `CartMapperTests.java` | `cart_item` SQL 映射、同 SKU 合并数量、用户隔离删除和购物车展示查询 |
| `CartServiceTests.java` | 购物车数量校验、SKU 存在性校验、用户隔离更新和删除异常 |
| `CartControllerTests.java` | `/api/cart/items` 鉴权、加购、改数量、删除、清空和用户隔离 |
| `MdcRequestIdInterceptorTests.java` | `X-Request-Id` 生成、透传、响应头回写和 MDC 清理 |
| `MySqlFlywayMigrationTests.java` | Testcontainers MySQL 环境下 Flyway 迁移可执行性 |

运行测试：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
```

如果要在本地跑真实 MySQL 容器测试，需要先启动 Docker，再设置：

```powershell
$env:RUN_MYSQL_TESTS='true'
.\mvnw.cmd -q -Dtest=MySqlFlywayMigrationTests test
```

## 当前本地数据库状态

当前本地开发数据库使用 MySQL 8.0：

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/intelligent_outfit
spring.datasource.username=root
spring.datasource.password=123456
```

Flyway 已执行：

| Migration | 作用 | 状态 |
|---|---|---|
| V1__product_inventory_schema.sql | 创建商品和库存表结构 | 成功 |
| V2__seed_demo_clothing_catalog.sql | 插入 demo 服装数据 | 成功 |
| V3__user_auth_profile_schema.sql | 创建用户认证、Refresh Token、登录日志和用户画像表结构 | 成功 |
| V4__conversation_schema.sql | 创建 AI 会话和消息历史表结构 | 成功 |
| V5__cart_schema.sql | 创建当前用户购物车条目表结构 | 成功 |

当前 demo 数据：

| 数据 | 数量 |
|---|---|
| SPU | 3 |
| SKU | 11 |
| 库存记录 | 11 |

可用下面的 SQL 验证：

```sql
SELECT COUNT(*) FROM product_spu;
SELECT COUNT(*) FROM product_sku;
SELECT COUNT(*) FROM inventory;
SELECT COUNT(*) FROM cart_item;
SELECT version, success FROM flyway_schema_history;
```

## 下一阶段开发建议

用户认证、双 Token 鉴权、用户资料和穿衣偏好模块已经完成。下一阶段建议优先补工程化能力和 AI 联动前置能力：先把 Testcontainers、Docker Compose、CI 和接口文档补齐，再开发会话记录与 Java 调 Python AI 服务。原因是 AI 推荐在进入真实链路前，需要稳定的用户上下文、可重复启动的本地环境、可验证的接口契约和可追踪的会话历史。

下一阶段要明确区分两套 token：

```text
Authorization: Bearer <accessToken>
  普通用户 API 使用，accessToken 为短效 JWT，refreshToken 由数据库 refresh_token 表管理。

X-Internal-Token
  Python AI 服务调用 Java internal API 使用，继续读取 application.properties 固定配置。
```

后续模块顺序建议：

1. engineering：Testcontainers、Docker Compose、GitHub Actions、Spring REST Docs
2. conversation-service：会话、消息历史、thread_id
3. assistant-service：Java 调 Python AI 服务，同步问答先跑通
4. cart-service：购物车
5. order-service：订单和库存锁定
6. mock-payment：模拟支付
7. SSE / WebSocket：流式返回
8. MQ：复杂推荐异步任务

## 后续维护方式

每新增一个后端功能，都需要同步更新本文档：

1. 在“功能总览”中增加一行。
2. 如果新增数据表，更新“数据库表对照”。
3. 如果新增接口，更新“API 对照”。
4. 如果新增测试，更新“测试对照”。
5. 如果当前阶段范围变化，更新“已实现”和“未实现”列表。

## 2026-06-02 订单 MVP 开发状态

订单模块已经进入第一版 Java 后端实现阶段，开发范围严格按 `docs/superpowers/plans/2026-06-02-order-mvp.md` 执行。本阶段只支持“购物车结算”，不支持“立即购买”，也不修改 Python AI 服务。

### 本阶段已实现

- `order` 标准分层：`api`、`service`、`mapper`、`model`、`dto`。
- Flyway 迁移 `V6__order_schema.sql`：
  - `sales_order`：订单主表，保存 `order_no`、`user_id`、`total_amount`、`status`、`paid_at`、创建和更新时间。
  - `order_item`：订单明细表，保存 SKU/SPU、商品名、分类、颜色、尺码、成交单价、数量、行金额、主图等下单快照。
- `InventoryMapper.lockStock`：
  - 使用 `UPDATE inventory SET available_stock = available_stock - ?, locked_stock = locked_stock + ? WHERE sku_id = ? AND available_stock >= ?` 完成原子库存锁定。
  - 这是“锁定库存”，不是最终销售扣减；支付阶段才会把 `locked_stock` 转成 `sold_stock`。
- `OrderService.createOrder`：
  - 只接受 `source=CART`。
  - 从 JWT 当前用户读取 `userId`，不信任前端传入用户信息。
  - 从 `cart_item` 和商品库重新读取数量、价格、商品名称、规格和库存事实。
  - 在后端重算 `totalAmount` 和每行 `lineAmount`。
  - 在同一个事务内完成库存锁定、订单主表插入、订单明细快照插入、购物车已购买 SKU 清理。
- 公开接口：
  - `POST /api/orders`：从当前用户购物车选中 SKU 创建 `UNPAID` 订单。
  - `GET /api/orders`：查询当前用户订单列表。
  - `GET /api/orders/{orderNo}`：按 `userId + orderNo` 查询当前用户订单详情。
- 测试覆盖：
  - `MySqlFlywayMigrationTests` 覆盖新订单表迁移存在性。
  - `OrderMapperTests` 覆盖订单插入、明细插入、用户隔离查询和购物车结算事实查询。
  - `InventoryMapperTests` 覆盖库存锁定成功、库存不足返回 0、库存从 available 移到 locked。
  - `OrderServiceTests` 覆盖后端重算金额、快照落库、库存不足、购物车 SKU 不存在和阶段性拒绝 `BUY_NOW`。
  - `OrderControllerTests` 覆盖鉴权、购物车结算、订单列表、订单详情、购物车清理、用户隔离和入参校验。

### 本阶段仍未实现

- `BUY_NOW` 立即购买：文档中已保留请求形态，当前接口会返回 `400 bad_request`。
- Mock Payment：暂未实现 `POST /api/payments/mock-pay`。
- 支付成功后的库存确认：暂未把 `locked_stock` 转为 `sold_stock`。
- 订单取消、超时关闭、锁定库存释放。
- 地址快照、优惠券、运费、发票、售后。
- Python Function Calling / Tool Use：AI 仍只推荐商品，不代客加购或下单。
- SSE / WebSocket 流式返回与 MQ 异步推荐任务。

### 本阶段接口契约

```http
POST /api/orders
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "source": "CART",
  "skuIds": [2103, 2203]
}
```

关键约束：

- `skuIds` 表示当前用户购物车中选中的 SKU。
- 数量来自 `cart_item.quantity`，前端不能在订单请求里传数量。
- 金额来自数据库实时 `sale_price`，前端不能传 `salePrice` 或 `totalAmount`。
- 如果任意 SKU 不属于当前用户购物车，返回 `404 not_found`。
- 如果库存不足，返回 `400 bad_request`，整个事务回滚。
- 成功后订单状态为 `UNPAID`，对应 SKU 会从当前用户购物车中移除。
