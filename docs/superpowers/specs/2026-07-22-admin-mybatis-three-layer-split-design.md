# Admin MyBatis 三层架构拆分设计

**日期：** 2026-07-22

**状态：** 已确认，待制定实施计划

**影响范围：** `Intelligent Outfit Recommendation System/backend` 的 `admin` 生产代码

**关联设计：** `2026-07-21-multi-turn-intent-lifecycle-and-admin-data-access-remediation-design.md`

**优先级：** 本文档是 Admin 数据访问治理的专项设计；如与上述关联设计的 Admin 拆分数量或归属不一致，以本文档为准。

## 1. 背景与现状

项目已配置 MyBatis：

- `mybatis-spring-boot-starter` 已引入；
- `mybatis.mapper-locations=classpath:mapper/**/*.xml` 已配置；
- 其他业务模块已采用 `Mapper Interface + Mapper XML`。

Admin 模块当前处于不完整迁移状态：

- `AdminController` 通过 `AdminCatalogService` 提供管理端接口；
- `AdminCatalogService` 已注入 `AdminMapper`，但同时仍注入 `JdbcTemplate`；
- 概览统计、商品查询等少量 SQL 已进入 `AdminMapper.xml`；
- 商品写入、分类、库存、订单、用户、分析和审计仍在 Service 中直接执行 JDBC；
- Service 中还包含 `PreparedStatement`、`ResultSet`、`KeyHolder`、SQL 字符串、行映射和动态 SQL 拼接；
- `AdminCatalogService` 已达 835 行，同时承担多个管理用例和持久化实现。

JDBC 本身没有过时，MyBatis 底层仍使用 JDBC。本次治理的问题是 Service 越过 Mapper seam 直接管理 SQL 与结果集，破坏了项目已有的数据访问规范和代码 locality。

## 2. 目标

生产代码统一遵循：

```text
Controller
  → Service
  → Mapper Interface
  → Mapper XML
  → MySQL
```

具体目标：

1. Admin Service 只负责参数校验、业务规则、事务编排、结果组装、审计触发和搜索同步触发。
2. Admin 全部 SQL 位于 `src/main/resources/mapper/admin` 下的 Mapper XML。
3. 按管理用例拆分巨型 `AdminCatalogService`，而不按数据库表机械拆分。
4. 移除 Admin 生产 Service 对 `JdbcTemplate` 及 `java.sql` 类型的依赖。
5. 保持现有 `/api/admin/**` 路由、JSON 契约、权限、数据库表结构和业务结果不变。
6. 用测试保护事务、生成主键、状态转换、审计与搜索同步语义。
7. 增加架构测试，防止 JDBC 和 SQL 重新回流 Service。

## 3. 非目标

- 不修改前端调用方式。
- 不修改数据库 schema，不改写已执行的 Flyway 历史迁移。
- 不在重构中调整商品、库存、订单或用户的业务规则。
- 不把 MyBatis 替换为 JPA、MyBatis-Plus 或新的数据访问框架。
- 不要求删除 `spring-boot-starter-jdbc`；Flyway、MyBatis 及其他模块仍可能依赖 JDBC 基础设施。
- 不禁止测试代码使用 `JdbcTemplate` 准备或断言数据。
- 不为只有一个简单 Mapper 调用的方法创建额外浅层。

## 4. 方案选择

### 4.1 采用：按管理用例拆分

采用六个业务 Service 和六个 Mapper：

```text
AdminProductService      → AdminProductMapper
AdminInventoryService    → AdminInventoryMapper
AdminOrderService        → AdminOrderMapper
AdminUserService         → AdminUserMapper
AdminAnalyticsService    → AdminAnalyticsMapper
AdminAuditLogService     → AdminAuditMapper
```

分类管理归入 `AdminProductService` 和 `AdminProductMapper`，因为分类名称变更需要批量触发所属商品的搜索索引同步，它与商品目录是同一管理用例。

审计写入是多个管理用例的内部持久化协作；商品、库存、订单和用户 Service 可直接注入 `AdminAuditMapper` 与本身 Mapper，使审计写入参与同一事务。`AdminAuditLogService` 只承担审计日志的查询用例，避免 Service 之间为简单持久化转发产生不必要的 seam。

### 4.2 拒绝：仅把 SQL 搬进一个巨型 XML

该方案改动小，但会保留 835 行 Service，并产生一个包含商品、库存、订单、用户、分析和审计的巨型 XML，只解决 SQL 位置，不解决模块职责。

### 4.3 拒绝：按数据库表拆分

按 SPU、SKU、库存、标签等表拆分会让“创建商品”被分散到多个浅模块，调用方必须了解底层表结构和执行顺序，降低模块深度与事务 locality。

## 5. 目标结构

Java 生产代码：

```text
admin
├── api
│   └── AdminController.java
├── service
│   ├── AdminProductService.java
│   ├── AdminInventoryService.java
│   ├── AdminOrderService.java
│   ├── AdminUserService.java
│   ├── AdminAnalyticsService.java
│   └── AdminAuditLogService.java
├── mapper
│   ├── AdminProductMapper.java
│   ├── AdminInventoryMapper.java
│   ├── AdminOrderMapper.java
│   ├── AdminUserMapper.java
│   ├── AdminAnalyticsMapper.java
│   └── AdminAuditMapper.java
├── model
│   ├── AdminProductPersistence.java
│   ├── AdminOrderState.java
│   ├── AdminOrderTrendRow.java
│   └── AdminFunnelStatistics.java
└── dto
    └── 保留现有请求与响应 DTO
```

Mapper XML：

```text
src/main/resources/mapper/admin
├── AdminProductMapper.xml
├── AdminInventoryMapper.xml
├── AdminOrderMapper.xml
├── AdminUserMapper.xml
├── AdminAnalyticsMapper.xml
└── AdminAuditMapper.xml
```

`model` 中只放置 Mapper 与 Service 之间确有必要的持久化输入或查询行模型。如果现有 DTO 能清晰且安全地表达查询结果，则直接复用，不为了形式对称创建重复模型。

## 6. Controller 与路由归属

保留一个 `AdminController`，不改变路由，改为注入六个业务 Service。

| 现有路由 | 目标 Service |
|---|---|
| `/api/admin/overview` | `AdminAnalyticsService` |
| `/api/admin/products/**` | `AdminProductService` |
| `/api/admin/categories/**` | `AdminProductService` |
| `/api/admin/inventory/**` | `AdminInventoryService` |
| `/api/admin/orders/**` | `AdminOrderService` |
| `/api/admin/users/**` | `AdminUserService` |
| `/api/admin/analytics` | `AdminAnalyticsService` |
| `/api/admin/audit-logs` | `AdminAuditLogService` |

Controller 只做 HTTP 入参绑定和 `ApiResponse` 包装，不增加业务判断或 Mapper 调用。

## 7. 各模块职责与 Mapper 接口

### 7.1 商品目录

`AdminProductService` 负责：

- 商品列表、创建、修改和上下架；
- 商品输入校验和状态标准化；
- 默认 SKU、初始库存和风格标签的事务编排；
- 分类列表与分类更新；
- 商品或分类名称改变后触发 `ProductSearchChangeRecorder`；
- 写入商品和分类管理审计。

`AdminProductMapper.xml` 承载：

- `findProducts`、`findProductById`；
- `insertProduct`、`updateProduct`、`updateProductStatus`；
- `updateSkuStatusBySpuId`；
- `findCategories`、`findCategoryById`、`findCategoryNameById`；
- `findProductIdsByCategoryId`、`updateCategory`；
- `findDefaultSkuBySpuId`、`insertDefaultSku`、`updateDefaultSku`；
- `insertInventoryIfAbsent`；
- `deleteStyleTagsBySpuId`、`insertStyleTag`。

新增 SPU 和 SKU 使用 MyBatis `useGeneratedKeys="true"` 与 `keyProperty`。持久化输入必须拥有可写的主键属性；不再使用 `GeneratedKeyHolder`、`PreparedStatement` 或 `Statement.RETURN_GENERATED_KEYS`。

### 7.2 库存

`AdminInventoryService` 负责：

- 库存列表；
- SKU 和目标库存校验；
- 读取调整前库存；
- 在同一事务中更新库存、写调整记录和审计记录；
- 返回调整后的 SKU 快照。

`AdminInventoryMapper.xml` 承载：

- `findInventory`、`findSkuById`；
- `findAvailableStockBySkuId`；
- `updateAvailableStock`；
- `insertInventoryAdjustment`。

删除 Java 中的 `inventorySql(condition)` 和 `mapSku(ResultSet, ...)`。列表与单条查询复用 XML `<sql>` 列片段，但保留明确的 Mapper 方法，不向 Java 传递 SQL 条件字符串。

### 7.3 订单履约

`AdminOrderService` 负责：

- 订单列表；
- 承运商与物流单号校验；
- 仅允许 `PAID` 订单发货；
- 在同一事务中更新订单状态、替换物流记录和写审计；
- 返回发货后订单快照。

`AdminOrderMapper.xml` 承载：

- `findOrders`、`findOrderByOrderNo`；
- `findOrderStateByOrderNo`；
- `markOrderShipped`；
- `deleteShipmentByOrderNo`、`insertShipment`。

引入类型明确的 `AdminOrderState`，替代 `Map<String, Object>` 和字符串键强制转换。发货状态更新必须带当前状态条件，例如 `WHERE id = #{orderId} AND status = 'PAID'`；如影响行数为零，Service 按业务冲突处理，防止查询与更新之间的状态竞态。

### 7.4 用户管理

`AdminUserService` 负责：

- 用户列表；
- 用户 ID 和状态校验；
- API 状态与数据库状态转换；
- 更新状态并写审计。

`AdminUserMapper.xml` 承载：

- `findUsers`、`findUserById`；
- `updateUserStatus`。

删除 Java 中的 `usersSql(condition)` 和 `mapUser(ResultSet, ...)`。

### 7.5 经营分析

`AdminAnalyticsService` 负责：

- 管理端概览和分析页响应组装；
- 空统计值归零；
- 按现有 `MM-dd` 规则聚合订单趋势，本次不改变现有时间范围或补零行为；
- 趋势标签格式化；
- 金额、热门商品、转化漏斗和分类趋势的业务组装。

`AdminAnalyticsMapper.xml` 承载：

- 在售商品、SKU、低库存、待发货、售后、订单等计数；
- 已支付金额聚合；
- 订单趋势原始行；
- 概览和分析热门商品；
- 转化漏斗统计；
- 分类趋势原始行。

SQL 负责返回按现有顺序排列的订单原始行，Service 负责日期标签和响应 DTO 组装。本次仅做数据访问等价迁移，不借重构之机新增 30 天过滤或缺失日期补零。

### 7.6 审计日志

`AdminAuditLogService` 负责审计日志查询和响应输出。

`AdminAuditMapper.xml` 承载：

- `insertAuditLog`；
- `findAuditLogs`。

写审计的 Service 传入类型化参数或一个小型持久化输入模型，不再在 Service 中保留 `INSERT` SQL。

## 8. 数据映射规则

1. 开启现有 `map-underscore-to-camel-case` 配置，简单查询可使用明确别名配合 `resultType`。
2. 多表联结、字段名冲突或类型转换较多的查询使用显式 `resultMap`。
3. SQL 中保持明确列表，禁止 `SELECT *`。
4. 公共列片段可用 `<sql>` 和 `<include>` 复用，但不为一行简单 SQL 建立多层间接引用。
5. 动态条件使用 `<if>`、`<where>` 或 `<set>`，所有外部值使用 `#{}` 参数绑定，禁止 `${}` 插入用户输入。
6. 列表查询保留当前稳定排序，避免迁移后前端顺序变化。
7. 不使用通用 `queryMap`、`queryNullable(sql, ...)` 或从 Service 传入 SQL 字符串的 Mapper 接口。

## 9. 事务与一致性

`@Transactional` 继续放在 Service 的公开业务方法上，Mapper 不启动独立事务。

必须保持的原子业务组：

| 用例 | 同一事务内的操作 |
|---|---|
| 创建商品 | 新增 SPU、默认 SKU、库存、标签、审计、搜索同步事件 |
| 修改商品 | 更新 SPU、默认 SKU、标签、审计、搜索同步事件 |
| 商品上下架 | SPU 状态、SKU 状态、审计、搜索同步事件 |
| 修改分类名称 | 分类更新、审计、受影响商品的搜索同步事件 |
| 调整库存 | 库存更新、调整历史、审计 |
| 订单发货 | 订单状态、物流记录、审计 |
| 修改用户状态 | 用户状态、审计 |

搜索变更记录必须继续与商品事实写入位于同一数据库事务，保持现有 outbox 一致性边界。

## 10. 错误处理

- 非法 ID、空必填文本、非法状态和负数库存继续抛出 `BadRequestException`。
- Mapper 单条查询无结果返回 `null`，由 Service 转换为 `ResourceNotFoundException`。
- 更新返回的影响行数为零时，Service 根据用例区分“资源不存在”与“当前状态不允许”。
- 不再通过 Service 捕获 `DataAccessException` 将任意数据库异常伪装成空结果；只对经确认的可预期唯一约束或并发冲突做明确转换。
- 数据库异常触发事务回滚，不留下主数据成功而审计或搜索事件缺失的部分状态。

## 11. 分阶段迁移方式

目标结构一次确定，实施按业务切片逐步进行，不在一次改动中盲目重写全部 835 行。

每个切片执行：

```text
固定现有行为的测试
→ 创建目标 Mapper 接口和 XML
→ 迁移该切片 SQL 与结果映射
→ 让目标 Service 承接对应用例
→ 让 Controller 对应路由切换到目标 Service
→ 删除原 Service 中该切片 JDBC 双路径
→ 运行模块测试和 Maven verify
```

迁移顺序：

1. 架构测试与当前 `AdminCatalogService` 临时例外基线；
2. `AdminAuditMapper` 与审计查询；
3. `AdminUserService + AdminUserMapper`；
4. `AdminInventoryService + AdminInventoryMapper`；
5. `AdminOrderService + AdminOrderMapper`；
6. `AdminProductService + AdminProductMapper`，包括分类；
7. `AdminAnalyticsService + AdminAnalyticsMapper`；
8. 删除已清空的 `AdminCatalogService`、旧 `AdminMapper` 和旧 `AdminMapper.xml`；
9. 移除架构测试临时例外，启用无例外最终规则。

顺序优先让审计入口可复用，然后从边界较清晰的用户和库存开始，最后迁移多表写入的商品和聚合查询较多的分析。

## 12. 测试与验证

### 12.1 行为回归

保留并扩展现有 `AdminControllerTests`，验证：

- 所有现有管理端路由与响应字段不变；
- 商品创建、修改和上下架；
- 分类改名后的搜索变更记录；
- 库存调整前后值、调整历史和审计；
- 仅 `PAID` 订单可发货；
- 用户状态转换；
- 概览、趋势、热门商品和漏斗数值不变。

### 12.2 Mapper 集成测试

为六个 Mapper 建立聚焦的 MyBatis 集成测试，覆盖：

- Java 参数与 XML statement ID 绑定；
- `resultMap` 字段与时间、金额、布尔值映射；
- 生成主键回填；
- 聚合和空数据结果；
- 稳定排序；
- 状态条件更新的影响行数。

### 12.3 事务测试

通过在事务后段制造失败，验证商品写入、库存调整和订单发货不留下部分成功数据。

### 12.4 架构测试

ArchUnit 最终规则限定 `src/main/java` 的 Service：

```text
..service.. 不得依赖 JdbcTemplate
..service.. 不得依赖 Connection
..service.. 不得依赖 PreparedStatement
..service.. 不得依赖 ResultSet
```

迁移期临时例外只允许现有 `AdminCatalogService`，且例外不得扩大。当该类删除后，同步删除例外。

另外扫描 Admin 生产 Service 源码，禁止出现内联 SQL 和从 Service 向 Mapper 传递 SQL 片段的通用接口。

### 12.5 最终验证

在 Windows 环境运行：

```text
backend\mvnw.cmd verify
```

只有全部测试、Checkstyle 和架构规则通过，才能宣布迁移完成。

## 13. 验收标准

- `AdminCatalogService` 已删除，现有路由由六个目标 Service 承接。
- Admin 生产 Service 中不存在 `JdbcTemplate`、`java.sql`、SQL 字符串、`ResultSet` 映射或主键回填实现。
- Admin 所有 SQL 位于六个目标 Mapper XML。
- Mapper Java 接口不使用注解 SQL，不接收 SQL 字符串参数。
- 现有 `/api/admin/**` 路由、响应 DTO、权限与前端行为不变。
- 商品、分类、库存、订单和用户变更的审计行为不变。
- 商品与分类变更的搜索同步事件不丢失。
- 订单发货使用状态条件更新，不将非 `PAID` 订单更新为 `SHIPPED`。
- 生成主键、金额、时间、布尔值和聚合结果映射正确。
- Maven `verify` 通过，架构测试对 JDBC 回流无例外。

## 14. 风险与控制

| 风险 | 控制措施 |
|---|---|
| XML 字段映射与 `ResultSet` 旧映射不一致 | Mapper 集成测试对每个返回字段断言，复杂查询用显式 `resultMap` |
| 生成主键无法回填 | 为 SPU 和 SKU 插入单独编写 MyBatis 集成测试 |
| 拆 Service 时事务被分断 | 一个完整管理用例只有一个顶层 Service 事务，审计 Mapper 参与该事务 |
| 迁移期新旧路径同时存在 | 每个路由切换后立即删除旧方法和 SQL，不保留双写或回退开关 |
| 分类与商品迁移影响搜索索引 | 特征测试固定受影响 SPU ID 和 outbox 事件数量 |
| 发货查询后状态被并发修改 | 更新 SQL 包含 `status = 'PAID'` 条件并校验影响行数 |
| 架构测试立即拦截现有 Service | 只对旧 `AdminCatalogService` 设临时例外，例外随迁移收缩并在最终删除 |

## 15. 完成定义

本次工作只有在六个业务模块全部承接现有管理用例、旧 `AdminCatalogService` 和旧 `AdminMapper` 被删除、生产 Service 中 JDBC/SQL 清零、全量验证通过后才算完成。仅将现有 SQL 搬进 XML，或只完成部分 Service 拆分，都不能标记为完成。
