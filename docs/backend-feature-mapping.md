# 后端功能对照表

本文档用于梳理 Intelligent Outfit Recommendation System 当前 Java 后端已经实现的功能、对应代码位置、数据库表、API 接口和测试用例。

当前阶段重点是：商品库、SKU、库存、服装细粒度属性，以及给 Python AI 推荐服务调用的 internal API。本文档也作为后续继续开发用户、购物车、订单、会话和 assistant-service 的功能追踪表。

## 当前阶段范围

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

### 未实现

- 用户注册登录
- 用户权限和 JWT
- 用户身材数据和穿衣偏好
- 购物车
- 订单
- 支付
- 会话记录
- Java 调 Python AI 服务
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
| `common/error/GlobalExceptionHandler.java` | 统一异常处理 |
| `common/internal/InternalApiProperties.java` | internal token 配置 |
| `common/internal/InternalApiInterceptor.java` | 校验 `X-Internal-Token` |
| `common/internal/WebMvcConfig.java` | 注册 `/internal/**` 拦截器 |

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
| flyway_schema_history | Flyway 数据库迁移记录 |

## API 对照

### 公开 API

公开 API 给商城前端或本地调试使用，不需要 internal token。

| API | 作用 | 是否需要 token |
|---|---|---|
| GET /api/products | 商品搜索 | 否 |
| GET /api/products/{spuId} | 商品详情 | 否 |
| GET /api/products/recommendation-candidates | 推荐候选商品查询 | 否 |

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

运行测试：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
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
SELECT version, success FROM flyway_schema_history;
```

## 下一阶段开发建议

建议下一阶段优先实现用户资料和穿衣偏好模块。原因是 AI 推荐在 Java 调 Python 之前，需要先有用户身高、体重、尺码、预算、颜色偏好、风格偏好等上下文。

后续模块顺序建议：

1. user-service：用户、登录、权限
2. user-profile-service：身材数据、尺码偏好、风格偏好
3. assistant-service：Java 调 Python AI 服务
4. conversation-service：会话和消息历史
5. cart-service：购物车
6. order-service：订单
7. SSE / WebSocket：流式返回
8. MQ：复杂推荐异步任务

## 后续维护方式

每新增一个后端功能，都需要同步更新本文档：

1. 在“功能总览”中增加一行。
2. 如果新增数据表，更新“数据库表对照”。
3. 如果新增接口，更新“API 对照”。
4. 如果新增测试，更新“测试对照”。
5. 如果当前阶段范围变化，更新“已实现”和“未实现”列表。
