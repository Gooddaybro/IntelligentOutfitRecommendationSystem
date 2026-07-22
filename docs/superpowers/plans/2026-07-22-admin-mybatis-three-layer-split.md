# Admin MyBatis Three-Layer Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Admin 生产代码从混合 `JdbcTemplate` 的巨型 Service 迁移为六个按管理用例拆分的 Service，并将全部 SQL 放入对应 MyBatis XML。

**Architecture:** 保留单一 `AdminController` 和现有 HTTP/JSON 契约，Controller 按路由调用 `AdminProductService`、`AdminInventoryService`、`AdminOrderService`、`AdminUserService`、`AdminAnalyticsService` 和 `AdminAuditLogService`。Service 保留业务校验、事务、审计与搜索同步编排；Mapper Interface 只声明类型化数据操作，SQL 全部在 `resources/mapper/admin/*.xml`。按审计、用户、库存、订单、商品、分析顺序逐切片迁移，每次切换路由后立即删除旧 JDBC 双路径。

**Tech Stack:** Java 21, Spring Boot 4, Spring MVC, Spring Transaction, MyBatis 4 starter, MySQL/H2, JUnit 5, AssertJ, MockMvc, ArchUnit, Maven Wrapper.

---

## 0. 实施前约束

- 工作目录：`D:\git\推荐系统\Intelligent Outfit Recommendation System\backend`
- 开发前必须阅读：`..\AGENTS.md`、`..\docs\commenting-guidelines.md`。
- 不得修改现有 `/api/admin/**` 路由、请求 DTO 或响应 DTO 字段。
- 不得修改 Flyway 历史迁移。
- 测试中的 `JdbcTemplate` 允许保留；禁止对象是 `src/main/java/**/service` 生产代码。
- 任务开始前记录 `git status --short`，不要纳入已存在的 `backend/src/test/java/.../learning/` 未跟踪内容。

## 1. 文件结构映射

### 新建生产文件

```text
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/
  AdminProductService.java          # 商品、分类、默认 SKU、标签和搜索同步编排
  AdminInventoryService.java        # 库存列表和手工调整事务
  AdminOrderService.java            # 订单列表和发货事务
  AdminUserService.java             # 用户列表和状态管理
  AdminAnalyticsService.java        # 概览、趋势、热门商品、漏斗和分类分析
  AdminAuditLogService.java         # 审计日志查询

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/
  AdminProductMapper.java
  AdminInventoryMapper.java
  AdminOrderMapper.java
  AdminUserMapper.java
  AdminAnalyticsMapper.java
  AdminAuditMapper.java

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/
  AdminAuditEntry.java              # 审计写入参数
  AdminInventoryRow.java            # 库存多表查询扁平行
  AdminOrderRow.java                # 订单多表查询扁平行
  AdminOrderState.java              # 发货前订单 ID/状态
  AdminOrderTrendRow.java           # 趋势查询原始行
  AdminProductWrite.java            # 可回填 SPU 主键的写入模型
  AdminSkuWrite.java                # 可回填 SKU 主键的写入模型

src/main/resources/mapper/admin/
  AdminProductMapper.xml
  AdminInventoryMapper.xml
  AdminOrderMapper.xml
  AdminUserMapper.xml
  AdminAnalyticsMapper.xml
  AdminAuditMapper.xml
```

### 新建测试文件

```text
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/
  AdminAuditMapperTests.java
  AdminUserMapperTests.java
  AdminInventoryMapperTests.java
  AdminOrderMapperTests.java
  AdminProductMapperTests.java
  AdminAnalyticsMapperTests.java
```

### 修改或删除文件

```text
Modify: src/main/java/.../admin/api/AdminController.java
Modify: src/main/java/.../admin/service/AdminCatalogService.java   # 随切片收缩，最后删除
Delete: src/main/java/.../admin/mapper/AdminMapper.java
Delete: src/main/resources/mapper/admin/AdminMapper.xml
Modify: src/test/java/.../admin/AdminControllerTests.java
Modify: src/test/java/.../architecture/ModuleArchitectureTests.java
```

---

### Task 1: 建立 JDBC 回流防线和特征测试基线

**Files:**
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminControllerTests.java`

- [ ] **Step 1: 在 ArchUnit 中加入迁移期规则**

在 `ModuleArchitectureTests` 增加下列规则；只为已存在的 `AdminCatalogService` 保留临时例外：

```java
@ArchTest
static final ArchRule NEW_ADMIN_SERVICES_MUST_NOT_DEPEND_ON_JDBC =
        noClasses().that().resideInAPackage(BASE_PACKAGE + ".admin.service..")
                .and().doNotHaveSimpleName("AdminCatalogService")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "org.springframework.jdbc.core.JdbcTemplate")
                .orShould().dependOnClassesThat().resideInAnyPackage("java.sql..");
```

- [ ] **Step 2: 补齐现有关键行为断言**

在 `AdminControllerTests` 现有用例中补充而不重复创建数据：

```java
assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM product_search_outbox WHERE spu_id = ?",
        Integer.class,
        1001L)).isPositive();

assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM admin_audit_log WHERE target_type = ? AND target_id = ?",
        Integer.class,
        "SPU",
        "1001")).isPositive();
```

在发货用例增加非 `PAID` 状态不允许发货的 HTTP 400 断言，并确认数据库状态未变。

- [ ] **Step 3: 运行基线测试**

Run:

```powershell
.\mvnw.cmd -Dtest=AdminControllerTests,ModuleArchitectureTests test
```

Expected: `BUILD SUCCESS`；新规则只排除 `AdminCatalogService`，其他 Admin Service 没有 JDBC 依赖。

- [ ] **Step 4: 提交防线和基线**

```powershell
git add src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminControllerTests.java
git commit -m "测试：锁定Admin迁移边界"
```

---

### Task 2: 迁移审计日志读写

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminAuditEntry.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminAuditMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminAuditLogService.java`
- Create: `src/main/resources/mapper/admin/AdminAuditMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminAuditMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`

- [ ] **Step 1: 先写 Mapper 失败测试**

```java
@ActiveProfiles("test")
@SpringBootTest
class AdminAuditMapperTests {
    @Autowired
    private AdminAuditMapper mapper;

    @Test
    @Transactional
    void insertsAndListsNewestAuditLog() {
        mapper.insertAuditLog(new AdminAuditEntry(
                "admin", "TEST_ACTION", "TEST", "42", "SUCCESS", "mapper migration"));

        assertThat(mapper.findAuditLogs(200))
                .first()
                .satisfies(row -> {
                    assertThat(row.action()).isEqualTo("TEST_ACTION");
                    assertThat(row.targetId()).isEqualTo("42");
                    assertThat(row.summary()).isEqualTo("mapper migration");
                });
    }
}
```

- [ ] **Step 2: 运行测试并确认失败**

```powershell
.\mvnw.cmd -Dtest=AdminAuditMapperTests test
```

Expected: test compilation fails because `AdminAuditMapper` and `AdminAuditEntry` do not exist.

- [ ] **Step 3: 建立类型化审计入口**

```java
public record AdminAuditEntry(
        String operator,
        String action,
        String targetType,
        String targetId,
        String result,
        String summary
) {
}
```

```java
@Mapper
public interface AdminAuditMapper {
    int insertAuditLog(AdminAuditEntry entry);

    List<AdminAuditLogResponse> findAuditLogs(@Param("limit") int limit);
}
```

```xml
<insert id="insertAuditLog">
    INSERT INTO admin_audit_log (operator, action, target_type, target_id, result, summary)
    VALUES (#{operator}, #{action}, #{targetType}, #{targetId}, #{result}, #{summary})
</insert>

<select id="findAuditLogs"
        resultType="com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminAuditLogResponse">
    SELECT id, operator, action, target_type, target_id, result, summary, created_at
    FROM admin_audit_log
    ORDER BY created_at DESC, id DESC
    LIMIT #{limit}
</select>
```

所有新建顶层 Java 类和 Mapper 按 `commenting-guidelines.md` 增加职责/边界 Javadoc。

- [ ] **Step 4: 迁移 Service 和 Controller**

`AdminAuditLogService` 实现：

```java
@Service
public class AdminAuditLogService {
    private static final int MAX_LOGS = 200;
    private final AdminAuditMapper adminAuditMapper;

    public AdminAuditLogService(AdminAuditMapper adminAuditMapper) {
        this.adminAuditMapper = adminAuditMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> listAuditLogs() {
        return adminAuditMapper.findAuditLogs(MAX_LOGS);
    }
}
```

将 `AdminCatalogService.insertAudit(...)` 改为构造 `AdminAuditEntry`并调用 `AdminAuditMapper.insertAuditLog`；删除审计 `JdbcTemplate.update` 和 `mapAuditLog`。`AdminController.listAuditLogs()` 切换到 `AdminAuditLogService`。

- [ ] **Step 5: 验证审计切片**

```powershell
.\mvnw.cmd -Dtest=AdminAuditMapperTests,AdminControllerTests,ModuleArchitectureTests test
```

Expected: all selected tests pass; `/api/admin/audit-logs` response stays unchanged.

- [ ] **Step 6: 提交审计迁移**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin/AdminAuditMapper.xml src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminAuditMapperTests.java
git commit -m "重构：迁移Admin审计数据访问"
```

---

### Task 3: 迁移用户管理

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminUserMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminUserService.java`
- Create: `src/main/resources/mapper/admin/AdminUserMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminUserMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`

- [ ] **Step 1: 先写用户 Mapper 失败测试**

```java
@Test
@Transactional
void listsAndUpdatesAdminUserProjection() {
    assertThat(mapper.findUsers()).extracting(AdminUserResponse::userId).contains(9001L);

    assertThat(mapper.updateUserStatus(9001L, "disabled")).isEqualTo(1);
    assertThat(mapper.findUserById(9001L).status()).isEqualTo("DISABLED");
}
```

Run: `.\mvnw.cmd -Dtest=AdminUserMapperTests test`

Expected: compilation fails because `AdminUserMapper` does not exist.

- [ ] **Step 2: 实现 Mapper Interface 和 XML**

```java
@Mapper
public interface AdminUserMapper {
    List<AdminUserResponse> findUsers();

    AdminUserResponse findUserById(@Param("userId") Long userId);

    int updateUserStatus(@Param("userId") Long userId, @Param("status") String status);
}
```

XML 使用一个 `adminUserColumns` 片段，列表和单条查询分别声明：

```xml
<sql id="adminUserColumns">
    u.id AS user_id, u.username, up.nickname, u.email, u.phone,
    CASE WHEN u.status = 'disabled' THEN 'DISABLED' ELSE 'ACTIVE' END AS status,
    u.created_at AS registered_at, COUNT(o.id) AS order_count,
    COALESCE(SUM(CASE WHEN o.status IN ('PAID', 'SHIPPED', 'COMPLETED')
                      THEN o.total_amount ELSE 0 END), 0) AS paid_amount
</sql>

<select id="findUserById" resultType="com.recommendation.intelligentoutfitrecommendationsystem.admin.dto.AdminUserResponse">
    SELECT <include refid="adminUserColumns"/>
    FROM user_account u
    LEFT JOIN user_profile up ON up.user_id = u.id
    LEFT JOIN sales_order o ON o.user_id = u.id
    WHERE u.id = #{userId}
    GROUP BY u.id, u.username, up.nickname, u.email, u.phone, u.status, u.created_at
</select>

<update id="updateUserStatus">
    UPDATE user_account
    SET status = #{status}, updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = #{userId}
</update>
```

`findUsers` 使用同一 FROM/GROUP BY，并保留 `ORDER BY u.id ASC`。

- [ ] **Step 3: 实现 `AdminUserService` 并切换路由**

Service 保留 `userId > 0`、`ACTIVE/DISABLED` 标准化、影响行数检查和审计写入：

```java
@Transactional
public AdminUserResponse changeUserStatus(Long userId, AdminUserStatusRequest request) {
    if (userId == null || userId <= 0) {
        throw new BadRequestException("userId must be positive");
    }
    String dbStatus = toDbUserStatus(request == null ? null : request.status());
    if (adminUserMapper.updateUserStatus(userId, dbStatus) == 0) {
        throw new ResourceNotFoundException("user not found");
    }
    AdminUserResponse user = requireUser(userId);
    adminAuditMapper.insertAuditLog(new AdminAuditEntry(
            "admin",
            "disabled".equals(dbStatus) ? "DISABLE_USER" : "ENABLE_USER",
            "USER", String.valueOf(userId), "SUCCESS", user.username()));
    return user;
}
```

`AdminController.listUsers/changeUserStatus` 改用 `AdminUserService`，然后从 `AdminCatalogService` 删除用户 public/private 方法、`usersSql` 和 `mapUser`。

- [ ] **Step 4: 运行并提交**

```powershell
.\mvnw.cmd -Dtest=AdminUserMapperTests,AdminControllerTests,ModuleArchitectureTests test
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin/AdminUserMapper.xml src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminUserMapperTests.java
git commit -m "重构：拆分Admin用户管理"
```

Expected: selected tests pass; old service no longer contains user SQL.

---

### Task 4: 迁移库存管理

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminInventoryRow.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminInventoryMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminInventoryService.java`
- Create: `src/main/resources/mapper/admin/AdminInventoryMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminInventoryMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`

- [ ] **Step 1: 写扁平行映射和更新失败测试**

```java
@Test
@Transactional
void readsLatestAdjustmentAndUpdatesAvailableStock() {
    Integer before = mapper.findAvailableStockBySkuId(2001L);
    mapper.insertInventoryAdjustment(2001L, before, 7, "mapper test", "admin");
    assertThat(mapper.updateAvailableStock(2001L, 7)).isEqualTo(1);

    AdminInventoryRow row = mapper.findSkuById(2001L);
    assertThat(row.availableStock()).isEqualTo(7);
    assertThat(row.adjustmentReason()).isEqualTo("mapper test");
}
```

Run: `.\mvnw.cmd -Dtest=AdminInventoryMapperTests test`

Expected: compilation fails because mapper and row model do not exist.

- [ ] **Step 2: 定义扁平行和 Mapper 接口**

`AdminInventoryRow` 字段必须完整包含：

```java
public record AdminInventoryRow(
        Long skuId, String skuCode, Long spuId, String productName,
        String color, String size, BigDecimal salePrice, int availableStock,
        String status, Integer beforeStock, Integer afterStock,
        String adjustmentReason, String adjustmentOperator, LocalDateTime adjustedAt
) {
}
```

```java
@Mapper
public interface AdminInventoryMapper {
    List<AdminInventoryRow> findInventory();
    AdminInventoryRow findSkuById(@Param("skuId") Long skuId);
    Integer findAvailableStockBySkuId(@Param("skuId") Long skuId);
    int updateAvailableStock(@Param("skuId") Long skuId, @Param("targetStock") int targetStock);
    int insertInventoryAdjustment(@Param("skuId") Long skuId,
                                  @Param("beforeStock") int beforeStock,
                                  @Param("afterStock") int afterStock,
                                  @Param("reason") String reason,
                                  @Param("operator") String operator);
}
```

- [ ] **Step 3: 迁移 XML SQL**

XML 使用 `adminInventoryColumns` 和 `adminInventoryFrom`，保留原查询的“最新一条调整记录”相关子查询：

```xml
LEFT JOIN admin_inventory_adjustment adj ON adj.id = (
    SELECT MAX(inner_adj.id)
    FROM admin_inventory_adjustment inner_adj
    WHERE inner_adj.sku_id = s.id
)
```

列别名明确与 `AdminInventoryRow` 对应：`adj.reason AS adjustment_reason`、`adj.operator AS adjustment_operator`、`adj.adjusted_at`；避免将持久化行直接映射成嵌套响应 DTO。

`findInventory` 保留 `ORDER BY s.id ASC`；`findSkuById` 使用 `WHERE s.id = #{skuId}`。更新和调整记录使用：

```xml
<update id="updateAvailableStock">
    UPDATE inventory
    SET available_stock = #{targetStock}, updated_at = CURRENT_TIMESTAMP(6)
    WHERE sku_id = #{skuId}
</update>

<insert id="insertInventoryAdjustment">
    INSERT INTO admin_inventory_adjustment
        (sku_id, before_stock, after_stock, reason, operator)
    VALUES (#{skuId}, #{beforeStock}, #{afterStock}, #{reason}, #{operator})
</insert>
```

- [ ] **Step 4: 实现 Service 映射和事务**

`AdminInventoryService` 用 `toResponse(AdminInventoryRow)` 创建 `AdminSkuResponse`；仅当 `adjustedAt != null` 时创建 `AdminInventoryAdjustmentResponse`。`adjustInventory` 保留原校验，并在同一 `@Transactional` 内执行库存更新、调整记录和 `AdminAuditMapper.insertAuditLog`。

Controller 的库存两个路由切换后，删除旧 Service 的 `listInventory`、`adjustInventory`、`findSku`、`inventorySql`、`mapSku`。

- [ ] **Step 5: 运行并提交**

```powershell
.\mvnw.cmd -Dtest=AdminInventoryMapperTests,AdminControllerTests,ModuleArchitectureTests test
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin/AdminInventoryMapper.xml src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminInventoryMapperTests.java
git commit -m "重构：拆分Admin库存管理"
```

---

### Task 5: 迁移订单履约并收紧并发状态

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminOrderRow.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminOrderState.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminOrderMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminOrderService.java`
- Create: `src/main/resources/mapper/admin/AdminOrderMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminOrderMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`

- [ ] **Step 1: 写订单映射与条件更新失败测试**

```java
@Test
@Transactional
void onlyPaidOrderCanTransitionToShipped() {
    AdminOrderState paid = mapper.findOrderStateByOrderNo("ORDDEMO9001PAID");
    assertThat(paid.status()).isEqualTo("PAID");
    assertThat(mapper.markOrderShipped(paid.orderId())).isEqualTo(1);
    assertThat(mapper.markOrderShipped(paid.orderId())).isZero();
}
```

```java
@Test
void mapsShipmentAndPaymentProjection() {
    AdminOrderRow row = mapper.findOrderByOrderNo("ORDDEMO9001PAID");
    assertThat(row.orderNo()).isEqualTo("ORDDEMO9001PAID");
    assertThat(row.paymentStatus()).isEqualTo("PAID");
}
```

Run: `.\mvnw.cmd -Dtest=AdminOrderMapperTests test`

Expected: compilation fails because new mapper/models do not exist.

- [ ] **Step 2: 定义订单模型和 Mapper**

```java
public record AdminOrderState(Long orderId, String orderNo, String status) {
}

public record AdminOrderRow(
        String orderNo, String username, String status, String paymentStatus,
        BigDecimal totalAmount, long itemCount, LocalDateTime createdAt,
        String carrier, String trackingNo
) {
}
```

```java
@Mapper
public interface AdminOrderMapper {
    List<AdminOrderRow> findOrders();
    AdminOrderRow findOrderByOrderNo(@Param("orderNo") String orderNo);
    AdminOrderState findOrderStateByOrderNo(@Param("orderNo") String orderNo);
    int markOrderShipped(@Param("orderId") Long orderId);
    int deleteShipmentByOrderNo(@Param("orderNo") String orderNo);
    int insertShipment(@Param("orderId") Long orderId, @Param("orderNo") String orderNo,
                       @Param("carrier") String carrier, @Param("trackingNo") String trackingNo);
}
```

- [ ] **Step 3: 迁移订单 SQL**

列表和单条查询复用原 `orderSql` 的列、JOIN、payment `CASE` 和 GROUP BY。`findOrders` 保留 `ORDER BY o.created_at DESC, o.id DESC`；单条查询使用 `WHERE o.order_no = #{orderNo}`。状态读取和条件更新必须为：

```xml
<select id="findOrderStateByOrderNo"
        resultType="com.recommendation.intelligentoutfitrecommendationsystem.admin.model.AdminOrderState">
    SELECT id AS order_id, order_no, status
    FROM sales_order
    WHERE order_no = #{orderNo}
</select>

<update id="markOrderShipped">
    UPDATE sales_order
    SET status = 'SHIPPED', updated_at = CURRENT_TIMESTAMP(6)
    WHERE id = #{orderId} AND status = 'PAID'
</update>
```

物流删除和新增分别使用 `order_no` 与 `(order_id, order_no, carrier, tracking_no)` 参数绑定。

- [ ] **Step 4: 实现发货事务**

`AdminOrderService.shipOrder` 先校验文本并读取 `AdminOrderState`；不存在抛 `ResourceNotFoundException`，非 `PAID` 抛 `BadRequestException("order cannot be shipped")`。即便初始读取为 `PAID`，`markOrderShipped` 返回 0 时也抛相同业务异常，阻止并发覆盖。成功后删除旧物流、插入新物流、写审计并返回 `findOrderByOrderNo` 映射的 DTO。

`toResponse(AdminOrderRow)` 保留现有 `availableActions`：`PAID→SHIP`、`UNPAID→CANCEL`、`SHIPPED/COMPLETED→AFTER_SALE`；`carrier == null` 时 shipment 为 null。

Controller 切换订单路由后，删除旧 Service 的订单方法、`orderSql`、`mapOrder`、`queryMap`。

- [ ] **Step 5: 运行并提交**

```powershell
.\mvnw.cmd -Dtest=AdminOrderMapperTests,AdminControllerTests,ModuleArchitectureTests test
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin/AdminOrderMapper.xml src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminOrderMapperTests.java
git commit -m "重构：拆分Admin订单履约"
```

---

### Task 6: 迁移商品、分类、SKU 与标签

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminProductWrite.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminSkuWrite.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminProductMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminProductService.java`
- Create: `src/main/resources/mapper/admin/AdminProductMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminProductMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`
- Modify: `src/main/resources/mapper/admin/AdminMapper.xml`

- [ ] **Step 1: 写生成主键、聚合和标签失败测试**

```java
@Test
@Transactional
void insertsProductAndSkuWithGeneratedKeys() {
    AdminProductWrite product = new AdminProductWrite(
            "ADMIN_MAPPER_TEST", "Mapper test product", 2L,
            "test", null, "draft");
    assertThat(mapper.insertProduct(product)).isEqualTo(1);
    assertThat(product.getId()).isPositive();

    AdminSkuWrite sku = new AdminSkuWrite(
            "ADMIN_MAPPER_TEST-DEFAULT", product.getId(), 1L, 1L,
            new BigDecimal("10.00"), new BigDecimal("20.00"), "off_sale");
    assertThat(mapper.insertDefaultSku(sku)).isEqualTo(1);
    assertThat(sku.getId()).isPositive();
}
```

```java
@Test
void readsProductCategoriesAndStyleTags() {
    assertThat(mapper.findProductById(1001L).getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
    assertThat(mapper.findCategories()).extracting(AdminCategoryResponse::id).contains(1L, 2L);
    assertThat(mapper.findProductStyleTags(1001L)).isNotNull();
}
```

Run: `.\mvnw.cmd -Dtest=AdminProductMapperTests test`

Expected: compilation fails because the new mapper and write models do not exist.

- [ ] **Step 2: 建立可回填主键的写入模型**

`AdminProductWrite` 只让 `id` 可由 MyBatis 回填，其他字段构造后不变：

```java
public class AdminProductWrite {
    private Long id;
    private final String spuCode;
    private final String name;
    private final Long categoryId;
    private final String description;
    private final String mainImageUrl;
    private final String status;

    public AdminProductWrite(String spuCode, String name, Long categoryId,
                             String description, String mainImageUrl, String status) {
        this.spuCode = spuCode;
        this.name = name;
        this.categoryId = categoryId;
        this.description = description;
        this.mainImageUrl = mainImageUrl;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSpuCode() { return spuCode; }
    public String getName() { return name; }
    public Long getCategoryId() { return categoryId; }
    public String getDescription() { return description; }
    public String getMainImageUrl() { return mainImageUrl; }
    public String getStatus() { return status; }
}
```

`AdminSkuWrite` 采用相同的主键回填方式：

```java
public class AdminSkuWrite {
    private Long id;
    private final String skuCode;
    private final Long spuId;
    private final Long colorId;
    private final Long sizeId;
    private final BigDecimal salePrice;
    private final BigDecimal originalPrice;
    private final String status;

    public AdminSkuWrite(String skuCode, Long spuId, Long colorId, Long sizeId,
                         BigDecimal salePrice, BigDecimal originalPrice, String status) {
        this.skuCode = skuCode;
        this.spuId = spuId;
        this.colorId = colorId;
        this.sizeId = sizeId;
        this.salePrice = salePrice;
        this.originalPrice = originalPrice;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSkuCode() { return skuCode; }
    public Long getSpuId() { return spuId; }
    public Long getColorId() { return colorId; }
    public Long getSizeId() { return sizeId; }
    public BigDecimal getSalePrice() { return salePrice; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public String getStatus() { return status; }
}
```

实际文件为两个顶层类增加说明主键回填边界的 Javadoc，getter/setter 按项目 Checkstyle 格式展开为多行。

- [ ] **Step 3: 定义完整 Mapper Interface**

```java
@Mapper
public interface AdminProductMapper {
    List<AdminProductResponse> findProducts();
    AdminProductResponse findProductById(@Param("spuId") Long spuId);
    long countProductById(@Param("spuId") Long spuId);
    int insertProduct(AdminProductWrite product);
    int updateProduct(AdminProductWrite product);
    int updateProductStatus(@Param("spuId") Long spuId, @Param("status") String status);
    int updateSkuStatusBySpuId(@Param("spuId") Long spuId, @Param("status") String status);

    List<AdminCategoryResponse> findCategories();
    AdminCategoryResponse findCategoryById(@Param("categoryId") Long categoryId);
    String findCategoryNameById(@Param("categoryId") Long categoryId);
    List<Long> findProductIdsByCategoryId(@Param("categoryId") Long categoryId);
    int updateCategory(@Param("categoryId") Long categoryId, @Param("name") String name,
                       @Param("status") String status, @Param("sortOrder") Integer sortOrder);

    Long findFirstSkuIdBySpuId(@Param("spuId") Long spuId);
    Long findFirstColorId();
    Long findFirstSizeId();
    int insertDefaultSku(AdminSkuWrite sku);
    int insertInventoryIfAbsent(@Param("skuId") Long skuId);
    int updateDefaultSku(AdminSkuWrite sku);
    int updateAllSkuStatuses(@Param("spuId") Long spuId, @Param("status") String status);

    List<String> findProductStyleTags(@Param("spuId") Long spuId);
    Long findStyleTagId(@Param("tag") String tag);
    int deleteStyleTagsBySpuId(@Param("spuId") Long spuId);
    int insertStyleTag(@Param("spuId") Long spuId, @Param("tagId") Long tagId);
}
```

- [ ] **Step 4: 迁移商品 XML**

将旧 `AdminMapper.xml` 的 `adminProductColumns`、`findProducts`、`findProductById`、`updateProductStatus` 移入 `AdminProductMapper.xml`，namespace 改为 `AdminProductMapper`。新增主键回填：

```xml
<insert id="insertProduct" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO product_spu (spu_code, name, category_id, description, main_image_url, status)
    VALUES (#{spuCode}, #{name}, #{categoryId}, #{description}, #{mainImageUrl}, #{status})
</insert>

<insert id="insertDefaultSku" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
    INSERT INTO product_sku
        (sku_code, spu_id, color_id, size_id, sale_price, original_price, status)
    VALUES
        (#{skuCode}, #{spuId}, #{colorId}, #{sizeId}, #{salePrice}, #{originalPrice}, #{status})
</insert>

<insert id="insertInventoryIfAbsent">
    INSERT INTO inventory (sku_id, available_stock, locked_stock, sold_stock)
    SELECT #{skuId}, 0, 0, 0
    WHERE NOT EXISTS (SELECT 1 FROM inventory WHERE sku_id = #{skuId})
</insert>
```

`updateCategory` 用 `<set>` 只在 `sortOrder != null` 时更新排序；标签 ID 查询保留 `WHERE code = #{tag} OR name = #{tag} ORDER BY id LIMIT 1`。所有用户可控值都使用 `#{}`。

- [ ] **Step 5: 实现商品 Service 事务**

将原 Service 的以下业务逻辑等价移入 `AdminProductService`：

```text
listProducts / createProduct / updateProduct / changeProductStatus
listCategories / updateCategory
normalizeProduct / normalizeProductInput / createOrUpdateDefaultSku
replaceStyleTags / toDbStatus / toApiStatus / toSkuStatus
normalizeRequiredText / trimToNull / nonNegativeMoney / defaultSkuCode
```

`createProduct` 使用 `AdminProductWrite.getId()` 获取 SPU 主键；新建 SKU 使用 `AdminSkuWrite.getId()` 初始化库存。商品、审计和 `ProductSearchChangeRecorder.record(spuId)` 保持在同一 `@Transactional` 内。

分类更新必须在更新前读取旧名；仅名称变化时查询受影响 SPU ID 并记录搜索变更。

- [ ] **Step 6: 切换路由并清理旧商品路径**

`AdminController` 的商品和分类路由改用 `AdminProductService`。从 `AdminCatalogService` 删除商品/分类所有 public/private 方法、`ProductInput`、`GeneratedKeyHolder`、`KeyHolder`、`PreparedStatement`、`Statement` 依赖。

- [ ] **Step 7: 运行并提交**

```powershell
.\mvnw.cmd -Dtest=AdminProductMapperTests,AdminControllerTests,ModuleArchitectureTests test
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminProductMapperTests.java
git commit -m "重构：拆分Admin商品目录管理"
```

---

### Task 7: 迁移概览和经营分析

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/model/AdminOrderTrendRow.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminAnalyticsMapper.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminAnalyticsService.java`
- Create: `src/main/resources/mapper/admin/AdminAnalyticsMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminAnalyticsMapperTests.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`
- Modify: `src/main/resources/mapper/admin/AdminMapper.xml`

- [ ] **Step 1: 写聚合和趋势失败测试**

```java
@Test
void readsCountsMoneyTrendAndHotProducts() {
    assertThat(mapper.countOnSaleProducts()).isNotNull();
    assertThat(mapper.countOrders()).isPositive();
    assertThat(mapper.sumPaidAmount()).isNotNull();
    assertThat(mapper.findOrderTrendRows()).isNotEmpty();
    assertThat(mapper.findAnalyticsHotProducts()).isNotNull();
    assertThat(mapper.findCategoryTrend()).isNotNull();
}

@Test
void funnelQueriesReturnZeroInsteadOfNull() {
    assertThat(mapper.countExposureEvents()).isNotNull();
    assertThat(mapper.countClickEvents()).isNotNull();
    assertThat(mapper.countCartEvents()).isNotNull();
    assertThat(mapper.countPurchasedOrders()).isNotNull();
}
```

Run: `.\mvnw.cmd -Dtest=AdminAnalyticsMapperTests test`

Expected: compilation fails because `AdminAnalyticsMapper` does not exist.

- [ ] **Step 2: 定义原始趋势行和 Mapper**

```java
public record AdminOrderTrendRow(
        String status,
        BigDecimal totalAmount,
        LocalDateTime createdAt
) {
}
```

```java
@Mapper
public interface AdminAnalyticsMapper {
    Long countOnSaleProducts();
    Long countSkus();
    Long countLowStockSkus(@Param("threshold") int threshold);
    Long countPendingShipmentOrders();
    Long countAfterSaleOrders();
    Long countOrders();
    BigDecimal sumPaidAmount();
    Long countExposureEvents();
    Long countClickEvents();
    Long countCartEvents();
    Long countPurchasedOrders();
    List<AdminOrderTrendRow> findOrderTrendRows();
    List<AdminAnalyticsHotProduct> findAnalyticsHotProducts();
    List<AdminCategoryTrendPoint> findCategoryTrend();
}
```

- [ ] **Step 3: 迁移统计 SQL**

将旧 `AdminMapper.xml` 的七个统计 statement 等价移入 `AdminAnalyticsMapper.xml`。漏斗使用固定 statement，不再从 Java 传 SQL：

```xml
<select id="countExposureEvents" resultType="long">
    SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%expos%'
</select>
<select id="countClickEvents" resultType="long">
    SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%click%'
</select>
<select id="countCartEvents" resultType="long">
    SELECT COUNT(*) FROM behavior_event WHERE LOWER(event_type) LIKE '%cart%'
</select>
<select id="countPurchasedOrders" resultType="long">
    SELECT COUNT(*) FROM sales_order WHERE status IN ('PAID', 'SHIPPED', 'COMPLETED')
</select>
```

`findOrderTrendRows` 保留 `ORDER BY created_at ASC, id ASC`；热销商品与分类趋势保留原 GROUP BY、排序和 `LIMIT 10`。

- [ ] **Step 4: 实现分析 Service**

`AdminAnalyticsService` 保留常量：

```java
private static final int LOW_STOCK_THRESHOLD = 5;
private static final String RANGE_LABEL = "\u6700\u8fd1 30 \u5929";
private static final DateTimeFormatter TREND_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");
private static final Set<String> PAID_ORDER_STATUSES = Set.of("PAID", "SHIPPED", "COMPLETED");
```

`findAnalyticsTrend` 遍历 `AdminOrderTrendRow`，按 `MM-dd` 累计订单数，只为 `PAID/SHIPPED/COMPLETED` 累计金额。`getOverview` 和 `getAnalytics` 保持现有 DTO 组装顺序和 `zero(...)` 语义。

Controller 切换 `/overview` 和 `/analytics`后，删除旧 Service 的分析方法、`safeCount`、`queryNullable`、`ResultSet`、`Timestamp`、`DataAccessException` 依赖。

- [ ] **Step 5: 运行并提交**

```powershell
.\mvnw.cmd -Dtest=AdminAnalyticsMapperTests,AdminControllerTests,ModuleArchitectureTests test
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/AdminAnalyticsMapperTests.java
git commit -m "重构：拆分Admin经营分析"
```

---

### Task 8: 删除旧混合层并启用无例外架构规则

**Files:**
- Delete: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java`
- Delete: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminMapper.java`
- Delete: `src/main/resources/mapper/admin/AdminMapper.xml`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/api/AdminController.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java`

- [ ] **Step 1: 确认 Controller 只依赖六个 Service**

Constructor 必须是：

```java
public AdminController(
        AdminProductService adminProductService,
        AdminInventoryService adminInventoryService,
        AdminOrderService adminOrderService,
        AdminUserService adminUserService,
        AdminAnalyticsService adminAnalyticsService,
        AdminAuditLogService adminAuditLogService
) {
    this.adminProductService = adminProductService;
    this.adminInventoryService = adminInventoryService;
    this.adminOrderService = adminOrderService;
    this.adminUserService = adminUserService;
    this.adminAnalyticsService = adminAnalyticsService;
    this.adminAuditLogService = adminAuditLogService;
}
```

用 `rg -n "AdminCatalogService|AdminMapper" src/main src/test` 确认除待删文件外无引用。

- [ ] **Step 2: 删除旧文件**

```powershell
git rm src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service/AdminCatalogService.java
git rm src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper/AdminMapper.java
git rm src/main/resources/mapper/admin/AdminMapper.xml
```

- [ ] **Step 3: 移除 ArchUnit 临时例外**

将 Task 1 规则改为：

```java
@ArchTest
static final ArchRule ADMIN_SERVICES_MUST_NOT_DEPEND_ON_JDBC =
        noClasses().that().resideInAPackage(BASE_PACKAGE + ".admin.service..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "org.springframework.jdbc.core.JdbcTemplate")
                .orShould().dependOnClassesThat().resideInAnyPackage("java.sql..");
```

- [ ] **Step 4: 扫描生产代码中的残留 JDBC/SQL**

```powershell
rg -n "JdbcTemplate|java\.sql|PreparedStatement|ResultSet|queryForObject|queryForMap" src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin
rg -n '"\s*(SELECT|INSERT|UPDATE|DELETE)\b|"""\s*(SELECT|INSERT|UPDATE|DELETE)\b' src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service
```

Expected: both commands return no matches. XML SQL remains under `src/main/resources/mapper/admin`.

- [ ] **Step 5: 验证旧类删除后的上下文**

```powershell
.\mvnw.cmd -Dtest=AdminControllerTests,AdminAuditMapperTests,AdminUserMapperTests,AdminInventoryMapperTests,AdminOrderMapperTests,AdminProductMapperTests,AdminAnalyticsMapperTests,ModuleArchitectureTests test
```

Expected: `BUILD SUCCESS`; Spring context 没有 Mapper statement 冲突或缺失。

- [ ] **Step 6: 提交最终清理**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin src/main/resources/mapper/admin src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/architecture/ModuleArchitectureTests.java
git commit -m "重构：移除Admin旧JDBC混合层"
```

---

### Task 9: 全量验证、证据检查与文档收尾

**Files:**
- Modify only if needed: `docs/superpowers/specs/2026-07-22-admin-mybatis-three-layer-split-design.md`
- Verify: all changed production and test files

- [ ] **Step 1: 运行完整 Maven verify**

```powershell
.\mvnw.cmd verify
```

Expected: `BUILD SUCCESS`; all JUnit tests, ArchUnit rules and Checkstyle pass.

- [ ] **Step 2: 核对六层切片数量和 XML namespace**

```powershell
Get-ChildItem src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service -Filter 'Admin*Service.java'
Get-ChildItem src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/mapper -Filter 'Admin*Mapper.java'
Get-ChildItem src/main/resources/mapper/admin -Filter 'Admin*Mapper.xml'
```

Expected: six target Service, six target Mapper, six target XML; no `AdminCatalogService` or old `AdminMapper`.

- [ ] **Step 3: 检查 SQL 归属和禁止模式**

```powershell
rg -n "JdbcTemplate|java\.sql|PreparedStatement|ResultSet" src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/admin/service
rg -n '\$\{' src/main/resources/mapper/admin
rg -n "AdminCatalogService|admin\.mapper\.AdminMapper" src/main src/test
```

Expected: no matches.

- [ ] **Step 4: 核对 Git 变更边界**

```powershell
git status --short
git diff --check f3bee12..HEAD
git log --oneline -8
```

Expected: 没有空白错误；未跟踪的 `learning/` 内容仍未被纳入；Admin 迁移按切片保留清晰提交。

- [ ] **Step 5: 如验证导致代码修正，单独提交收尾**

```powershell
git add src/main src/test docs
git commit -m "测试：完成Admin三层架构验收"
```

若 Step 1–4 没有产生任何新变更，不创建空提交。

---

## 实施完成核对表

- [ ] 六个管理 Service 全部存在且 Controller 路由归属正确。
- [ ] 六个 Mapper Interface 与六个 Mapper XML namespace/statement 完全对应。
- [ ] 生产 Service 中没有 JDBC 类型、SQL 字符串或结果集映射。
- [ ] 商品 SPU/SKU 主键通过 MyBatis 成功回填。
- [ ] 审计和商品搜索同步记录仍与主数据位于同一事务。
- [ ] 订单发货 SQL 带 `status = 'PAID'` 条件并检查影响行数。
- [ ] 前端使用的 HTTP 路由和 JSON 字段未变。
- [ ] 架构测试无 `AdminCatalogService` 例外。
- [ ] `.\mvnw.cmd verify` 完整通过。
