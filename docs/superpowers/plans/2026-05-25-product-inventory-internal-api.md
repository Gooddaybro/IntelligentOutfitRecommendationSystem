# 商品库存 Internal API 第一阶段开发计划

> **给后续执行者：** 实施本计划时需要使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans`，按任务逐步执行。所有步骤都使用 checkbox（`- [ ]`）追踪状态。

**目标：** 完成服装推荐商城后端的第一阶段能力：商品目录表、服装细粒度属性、库存查询、Demo 种子数据，以及 Python AI 服务可调用的 Java internal 商品事实接口。

**架构：** 使用 Spring Boot 模块化单体，先通过 SQL migration 固化数据库结构，再用偏查询型的 JDBC Repository 实现商品事实查询。本阶段不做购物车、订单、用户登录、Java 调 Python，只先建立后续模块和 Python LangGraph Agent 依赖的商品/库存事实源。

**技术栈：** Java 21、Spring Boot 4、Spring WebMVC、Spring JDBC、Flyway、MySQL、H2 测试库、JUnit 5、MockMvc。

---

## 范围

本计划实现 [Java 电商后端与 Python AI 导购服务架构设计](../../architecture/java-ai-clothing-mall-architecture.md) 中的第一个可独立验收阶段。

本阶段包含：

- 商品目录表：SPU、SKU、颜色、尺码、材质、版型、季节、风格标签、图片、扩展属性和关系表。
- SKU 可售库存表。
- 与当前 Python 项目示例匹配的 Demo 服装数据：基础款纯棉 T 恤、通勤轻薄外套、直筒休闲长裤。
- Java 查询服务：商品搜索、SKU 查询、库存查询、商品详情、推荐候选商品查询。
- 给 Python 调用的 internal API：
  - `GET /internal/products/search`
  - `GET /internal/products/{spuId}`
  - `GET /internal/skus/search`
  - `GET /internal/inventory`
  - `GET /internal/recommendation-candidates`
- 通过 `X-Internal-Token` 做简单内部接口鉴权。
- 提供公开只读商品 API，方便本地手动检查。

本阶段不包含：

- 用户登录和 JWT。
- 用户身体信息、风格偏好表。
- 购物车、订单、支付、库存锁定。
- Java 调 Python `/chat`。
- Python 项目代码改造。
- SSE 流式聊天和 MQ 异步任务。

当前工作区说明：

```text
D:\git\Intelligent Outfit Recommendation System 当前不是 Git 仓库。
本计划里的 commit 步骤只有在该目录初始化为 Git 仓库后才执行。
```

## 文件结构

本阶段会创建或修改这些文件：

```text
pom.xml
src/main/resources/application.properties
src/main/resources/db/migration/V1__product_inventory_schema.sql
src/main/resources/db/migration/V2__seed_demo_clothing_catalog.sql
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/api/ApiResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/api/ErrorResponse.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/BadRequestException.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/ResourceNotFoundException.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/GlobalExceptionHandler.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/InternalApiProperties.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/InternalApiInterceptor.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/WebMvcConfig.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductSearchItem.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductDetail.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/SkuSearchItem.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidate.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository/ProductQueryRepository.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/model/InventoryView.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository/InventoryQueryRepository.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service/InventoryQueryService.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/api/InternalInventoryController.java
src/test/resources/application-test.properties
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogRepositoryTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/InternalProductControllerTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InternalInventoryControllerTests.java
```

第一版使用 JDBC，不使用 JPA。原因是本阶段以查询为主，并且采用 schema-first 的方式先确定数据库结构。JDBC 能让 SQL join 更明确，避免过早引入复杂实体关系，也方便为 Python-facing internal API 提供稳定的读模型。

## 任务 1: 增加持久化依赖和测试配置

**文件：**
- 修改：`pom.xml`
- 修改：`src/main/resources/application.properties`
- 新建：`src/test/resources/application-test.properties`

- [ ] **步骤 1: 确认当前依赖还不完整**

打开 `pom.xml`，确认下面这些依赖当前还没有配置：

```xml
<artifactId>spring-boot-starter-jdbc</artifactId>
<artifactId>spring-boot-starter-validation</artifactId>
<artifactId>flyway-core</artifactId>
<artifactId>h2</artifactId>
```

运行：

```powershell
.\mvnw.cmd -q test
```

预期：现有 context test 可能会通过，但此时还没有数据库 migration，也没有 JDBC 相关测试。

- [ ] **步骤 2: 增加依赖**

修改 `pom.xml`，在 `<dependencies>` 下加入：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

- [ ] **步骤 3: 配置默认 application.properties**

把 `src/main/resources/application.properties` 设置为：

```properties
spring.application.name=IntelligentOutfitRecommendationSystem

spring.datasource.url=jdbc:mysql://localhost:3306/intelligent_outfit?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

app.internal-api.token=dev-internal-token
```

- [ ] **步骤 4: 增加测试环境配置**

新建 `src/test/resources/application-test.properties`：

```properties
spring.datasource.url=jdbc:h2:mem:intelligent_outfit_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration

app.internal-api.token=test-internal-token
```

- [ ] **步骤 5: 运行测试**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：现有 context test 通过；如果失败，原因应只与 migration 文件还没有创建有关。遇到 migration 路径或迁移文件缺失错误时，继续执行任务 2。

- [ ] **步骤 6: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add pom.xml src/main/resources/application.properties src/test/resources/application-test.properties
git commit -m "chore: add jdbc migration test configuration"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 2。

## 任务 2: 创建商品和库存数据库 Migration

**文件：**
- 新建：`src/main/resources/db/migration/V1__product_inventory_schema.sql`
- 新建：`src/main/resources/db/migration/V2__seed_demo_clothing_catalog.sql`
- 测试：`src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplicationTests.java`

- [ ] **步骤 1: 修改 context test，让它使用测试配置**

修改现有 context test，启用 `test` profile：

```java
package com.recommendation.intelligentoutfitrecommendationsystem;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class IntelligentOutfitRecommendationSystemApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

运行：

```powershell
.\mvnw.cmd -q test
```

预期：失败，因为 migration 文件还不存在，数据库结构还没有创建。

- [ ] **步骤 2: 创建数据库结构 migration**

创建 `src/main/resources/db/migration/V1__product_inventory_schema.sql`，内容如下：

```sql
CREATE TABLE category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT NULL,
    name VARCHAR(64) NOT NULL,
    level INT NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE TABLE color (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(32) NOT NULL,
    color_family VARCHAR(32) NOT NULL,
    hex_code VARCHAR(16) NOT NULL,
    UNIQUE KEY uk_color_name (name)
);

CREATE TABLE size_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(16) NOT NULL,
    name VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_size_code (code)
);

CREATE TABLE fit_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_fit_type_code (code)
);

CREATE TABLE season (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    UNIQUE KEY uk_season_code (code)
);

CREATE TABLE style_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_style_tag_code (code)
);

CREATE TABLE material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_material_name (name)
);

CREATE TABLE size_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    category_id BIGINT NOT NULL,
    rule_json TEXT NOT NULL,
    description VARCHAR(255) NULL,
    UNIQUE KEY uk_size_rule_code (code),
    CONSTRAINT fk_size_rule_category FOREIGN KEY (category_id) REFERENCES category(id)
);

CREATE TABLE product_spu (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_code VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    category_id BIGINT NOT NULL,
    brand_id BIGINT NULL,
    description TEXT NULL,
    main_image_url VARCHAR(512) NULL,
    fit_type_id BIGINT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_product_spu_code (spu_code),
    KEY idx_product_spu_name (name),
    KEY idx_product_spu_status (status),
    CONSTRAINT fk_product_spu_category FOREIGN KEY (category_id) REFERENCES category(id),
    CONSTRAINT fk_product_spu_fit_type FOREIGN KEY (fit_type_id) REFERENCES fit_type(id)
);

CREATE TABLE product_sku (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_code VARCHAR(64) NOT NULL,
    spu_id BIGINT NOT NULL,
    color_id BIGINT NOT NULL,
    size_id BIGINT NOT NULL,
    sale_price DECIMAL(10,2) NOT NULL,
    original_price DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_product_sku_code (sku_code),
    UNIQUE KEY uk_product_sku_spec (spu_id, color_id, size_id),
    KEY idx_product_sku_spu (spu_id),
    CONSTRAINT fk_product_sku_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_sku_color FOREIGN KEY (color_id) REFERENCES color(id),
    CONSTRAINT fk_product_sku_size FOREIGN KEY (size_id) REFERENCES size_option(id)
);

CREATE TABLE product_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    sku_id BIGINT NULL,
    image_url VARCHAR(512) NOT NULL,
    image_type VARCHAR(32) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_product_image_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_image_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);

CREATE TABLE product_material (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    percentage INT NULL,
    UNIQUE KEY uk_product_material (spu_id, material_id),
    CONSTRAINT fk_product_material_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_material_material FOREIGN KEY (material_id) REFERENCES material(id)
);

CREATE TABLE product_season (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    season_id BIGINT NOT NULL,
    UNIQUE KEY uk_product_season (spu_id, season_id),
    CONSTRAINT fk_product_season_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_season_season FOREIGN KEY (season_id) REFERENCES season(id)
);

CREATE TABLE product_style_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    style_tag_id BIGINT NOT NULL,
    UNIQUE KEY uk_product_style_tag (spu_id, style_tag_id),
    CONSTRAINT fk_product_style_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id),
    CONSTRAINT fk_product_style_tag FOREIGN KEY (style_tag_id) REFERENCES style_tag(id)
);

CREATE TABLE product_attribute (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    spu_id BIGINT NOT NULL,
    attr_name VARCHAR(64) NOT NULL,
    attr_value VARCHAR(128) NOT NULL,
    KEY idx_product_attribute_name_value (attr_name, attr_value),
    CONSTRAINT fk_product_attribute_spu FOREIGN KEY (spu_id) REFERENCES product_spu(id)
);

CREATE TABLE inventory (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku_id BIGINT NOT NULL,
    available_stock INT NOT NULL,
    locked_stock INT NOT NULL DEFAULT 0,
    sold_stock INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_inventory_sku (sku_id),
    CONSTRAINT fk_inventory_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id)
);
```

`size_option` is used instead of a table named `size` to avoid SQL keyword conflicts while still exposing the API field as `size`.

- [ ] **步骤 3: 创建 Demo 商品种子数据 migration**

创建 `src/main/resources/db/migration/V2__seed_demo_clothing_catalog.sql`，使用固定 ID，方便测试稳定断言：

```sql
INSERT INTO category (id, parent_id, name, level, sort_order, status) VALUES
(1, NULL, '上衣', 1, 1, 'active'),
(2, 1, 'T恤', 2, 1, 'active'),
(3, 1, '外套', 2, 2, 'active'),
(4, NULL, '下装', 1, 2, 'active'),
(5, 4, '长裤', 2, 1, 'active');

INSERT INTO color (id, name, color_family, hex_code) VALUES
(1, '黑色', 'black', '#000000'),
(2, '白色', 'white', '#FFFFFF'),
(3, '灰色', 'gray', '#808080'),
(4, '藏青色', 'blue', '#1F2A44'),
(5, '卡其色', 'khaki', '#C3B091'),
(6, '深蓝色', 'blue', '#003366');

INSERT INTO size_option (id, code, name, sort_order) VALUES
(1, 'S', 'S码', 1),
(2, 'M', 'M码', 2),
(3, 'L', 'L码', 3),
(4, 'XL', 'XL码', 4);

INSERT INTO fit_type (id, code, name, description) VALUES
(1, 'regular', '合身', '常规版型，适合大多数日常场景'),
(2, 'loose', '宽松', '宽松版型，适合偏休闲穿着'),
(3, 'straight', '直筒', '直筒版型，适合裤装');

INSERT INTO season (id, code, name) VALUES
(1, 'spring', '春季'),
(2, 'summer', '夏季'),
(3, 'autumn', '秋季'),
(4, 'winter', '冬季'),
(5, 'all_season', '四季');

INSERT INTO style_tag (id, code, name, description) VALUES
(1, 'commute', '通勤', '适合上班、通勤和半正式场景'),
(2, 'casual', '休闲', '适合日常休闲场景'),
(3, 'minimal', '极简', '配色和设计简洁'),
(4, 'sport', '运动', '适合轻运动或运动休闲');

INSERT INTO material (id, name, description) VALUES
(1, '纯棉', '亲肤、透气，适合基础款上衣'),
(2, '聚酯纤维混纺', '轻薄、抗皱，适合外套'),
(3, '棉涤混纺', '兼顾挺括和舒适，适合裤装');

INSERT INTO size_rule (id, code, name, category_id, rule_json, description) VALUES
(1, 'default_tshirt', '默认T恤尺码规则', 2, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":170,"heightMax":182}]}', 'T恤基础尺码规则'),
(2, 'default_jacket', '默认外套尺码规则', 3, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":172,"heightMax":183}]}', '外套基础尺码规则'),
(3, 'default_pants', '默认长裤尺码规则', 5, '{"type":"height_weight","ranges":[{"size":"M","heightMin":165,"heightMax":175},{"size":"L","heightMin":170,"heightMax":182}]}', '长裤基础尺码规则');

INSERT INTO product_spu (id, spu_code, name, category_id, description, main_image_url, fit_type_id, status) VALUES
(1001, 'TSHIRT_BASIC_001', '基础款纯棉T恤', 2, '100%纯棉基础款T恤，适合日常内搭和单穿。', '/images/products/tshirt-basic-main.jpg', 1, 'on_sale'),
(1002, 'JACKET_COMMUTE_001', '通勤轻薄外套', 3, '轻薄通勤外套，适合春秋通勤和日常外出。', '/images/products/jacket-commute-main.jpg', 1, 'on_sale'),
(1003, 'PANTS_STRAIGHT_001', '直筒休闲长裤', 5, '直筒休闲长裤，适合日常和通勤场景。', '/images/products/pants-straight-main.jpg', 3, 'on_sale');

INSERT INTO product_sku (id, sku_code, spu_id, color_id, size_id, sale_price, original_price, status) VALUES
(2001, 'TS-BASIC-001-BLK-S', 1001, 1, 1, 99.00, 129.00, 'on_sale'),
(2002, 'TS-BASIC-001-BLK-M', 1001, 1, 2, 99.00, 129.00, 'on_sale'),
(2003, 'TS-BASIC-001-BLK-L', 1001, 1, 3, 99.00, 129.00, 'on_sale'),
(2004, 'TS-BASIC-001-BLK-XL', 1001, 1, 4, 99.00, 129.00, 'on_sale'),
(2005, 'TS-BASIC-001-WHT-L', 1001, 2, 3, 99.00, 129.00, 'on_sale'),
(2101, 'JK-COMMUTE-001-BLK-M', 1002, 1, 2, 299.00, 399.00, 'on_sale'),
(2102, 'JK-COMMUTE-001-BLK-L', 1002, 1, 3, 299.00, 399.00, 'on_sale'),
(2103, 'JK-COMMUTE-001-NAVY-L', 1002, 4, 3, 299.00, 399.00, 'on_sale'),
(2201, 'PANTS-STRAIGHT-001-BLK-M', 1003, 1, 2, 199.00, 259.00, 'on_sale'),
(2202, 'PANTS-STRAIGHT-001-BLK-L', 1003, 1, 3, 199.00, 259.00, 'on_sale'),
(2203, 'PANTS-STRAIGHT-001-BLUE-L', 1003, 6, 3, 199.00, 259.00, 'on_sale');

INSERT INTO product_image (spu_id, sku_id, image_url, image_type, sort_order) VALUES
(1001, NULL, '/images/products/tshirt-basic-main.jpg', 'main', 1),
(1002, NULL, '/images/products/jacket-commute-main.jpg', 'main', 1),
(1003, NULL, '/images/products/pants-straight-main.jpg', 'main', 1);

INSERT INTO product_material (spu_id, material_id, percentage) VALUES
(1001, 1, 100),
(1002, 2, 100),
(1003, 3, 100);

INSERT INTO product_season (spu_id, season_id) VALUES
(1001, 2),
(1001, 5),
(1002, 1),
(1002, 3),
(1003, 1),
(1003, 3),
(1003, 5);

INSERT INTO product_style_tag (spu_id, style_tag_id) VALUES
(1001, 2),
(1001, 3),
(1002, 1),
(1002, 3),
(1003, 1),
(1003, 2);

INSERT INTO product_attribute (spu_id, attr_name, attr_value) VALUES
(1001, '厚度', '常规'),
(1001, '弹力', '微弹'),
(1001, '领型', '圆领'),
(1002, '厚度', '薄款'),
(1002, '抗皱', '较好'),
(1002, '适用场景', '通勤'),
(1003, '裤型', '直筒'),
(1003, '厚度', '常规'),
(1003, '适用场景', '通勤');

INSERT INTO inventory (sku_id, available_stock, locked_stock, sold_stock) VALUES
(2001, 6, 0, 0),
(2002, 12, 0, 0),
(2003, 8, 0, 0),
(2004, 0, 0, 0),
(2005, 2, 0, 0),
(2101, 4, 0, 0),
(2102, 7, 0, 0),
(2103, 5, 0, 0),
(2201, 8, 0, 0),
(2202, 6, 0, 0),
(2203, 4, 0, 0);
```

- [ ] **步骤 4: 通过测试运行 migration**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：PASS。Spring context 应该能使用 H2 和 Flyway migrations 正常启动。

- [ ] **步骤 5: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/resources/db/migration src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplicationTests.java
git commit -m "feat: add product inventory schema"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 3。

## 任务 3: 增加通用响应结构和 internal API 鉴权

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/api/ApiResponse.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/api/ErrorResponse.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/BadRequestException.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/ResourceNotFoundException.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/error/GlobalExceptionHandler.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/InternalApiProperties.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/InternalApiInterceptor.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/internal/WebMvcConfig.java`

- [ ] **步骤 1: 创建通用响应 record**

创建 `ApiResponse.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.api;

public record ApiResponse<T>(
        boolean success,
        T data,
        String errorCode,
        String message
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, "ok");
    }

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, errorCode, message);
    }
}
```

创建 `ErrorResponse.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.api;

public record ErrorResponse(
        String errorCode,
        String message
) {
}
```

- [ ] **步骤 2: 创建业务异常**

创建 `BadRequestException.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
```

创建 `ResourceNotFoundException.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **步骤 3: 创建全局异常处理器**

创建 `GlobalExceptionHandler.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.error;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBadRequest(BadRequestException exception) {
        return ApiResponse.error("bad_request", exception.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(ResourceNotFoundException exception) {
        return ApiResponse.error("not_found", exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("request validation failed");
        return ApiResponse.error("validation_failed", message);
    }
}
```

- [ ] **步骤 4: 创建 internal API 配置类**

创建 `InternalApiProperties.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.internal-api")
public record InternalApiProperties(String token) {
}
```

- [ ] **步骤 5: 创建 internal API 拦截器**

创建 `InternalApiInterceptor.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalApiInterceptor implements HandlerInterceptor {

    private static final String HEADER_NAME = "X-Internal-Token";

    private final InternalApiProperties properties;

    public InternalApiInterceptor(InternalApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String expectedToken = properties.token();
        String actualToken = request.getHeader(HEADER_NAME);
        if (expectedToken != null && expectedToken.equals(actualToken)) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"data\":null,\"errorCode\":\"internal_unauthorized\",\"message\":\"invalid internal token\"}");
        return false;
    }
}
```

- [ ] **步骤 6: 注册拦截器**

创建 `WebMvcConfig.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.common.internal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(InternalApiProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final InternalApiInterceptor internalApiInterceptor;

    public WebMvcConfig(InternalApiInterceptor internalApiInterceptor) {
        this.internalApiInterceptor = internalApiInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalApiInterceptor)
                .addPathPatterns("/internal/**");
    }
}
```

- [ ] **步骤 7: 运行测试**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：PASS。

- [ ] **步骤 8: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common
git commit -m "feat: add common api and internal guard"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 4。

## 任务 4: 实现商品查询 Repository

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductSearchItem.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductDetail.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/SkuSearchItem.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidate.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository/ProductQueryRepository.java`
- 测试：`src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogRepositoryTests.java`

- [ ] **步骤 1: 先写失败的 Repository 测试**

创建 `ProductCatalogRepositoryTests.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.repository.ProductQueryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@JdbcTest
@Import(ProductQueryRepository.class)
class ProductCatalogRepositoryTests {

    @Autowired
    private ProductQueryRepository repository;

    @Test
    void searchProductsFindsBasicTshirtByKeyword() {
        var results = repository.searchProducts("纯棉T恤");

        assertThat(results).extracting("spuCode").contains("TSHIRT_BASIC_001");
    }

    @Test
    void findSkuReturnsBlackLargeTshirt() {
        var sku = repository.findSku(1001L, "黑色", "L").orElseThrow();

        assertThat(sku.skuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(sku.salePrice().intValue()).isEqualTo(99);
    }

    @Test
    void findRecommendationCandidatesFiltersByStyleSeasonAndBudget() {
        var candidates = repository.findRecommendationCandidates("外套", "commute", "autumn", null, null, 400);

        assertThat(candidates).extracting("spuCode").contains("JACKET_COMMUTE_001");
    }
}
```

运行：

```powershell
.\mvnw.cmd -q -Dtest=ProductCatalogRepositoryTests test
```

预期：失败，因为 model 和 repository 类还不存在。

- [ ] **步骤 2: 创建商品查询读模型 records**

创建 `ProductSearchItem.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record ProductSearchItem(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String mainImageUrl,
        String fitType,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
```

创建 `SkuSearchItem.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record SkuSearchItem(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        BigDecimal salePrice,
        String status
) {
}
```

创建 `RecommendationCandidate.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;

public record RecommendationCandidate(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String mainImageUrl,
        String fitType,
        String materials,
        String seasons,
        String styleTags,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Integer totalAvailableStock
) {
}
```

创建 `ProductDetail.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductDetail(
        Long spuId,
        String spuCode,
        String name,
        String categoryName,
        String description,
        String mainImageUrl,
        String fitType,
        List<String> materials,
        List<String> seasons,
        List<String> styleTags,
        Map<String, String> attributes,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}
```

- [ ] **步骤 3: 实现 Repository**

创建 `ProductQueryRepository.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.repository;

import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class ProductQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProductQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        String sql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.main_image_url,
                    f.name AS fit_type,
                    MIN(s.sale_price) AS min_price,
                    MAX(s.sale_price) AS max_price
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku s ON s.spu_id = p.id
                WHERE p.status = 'on_sale'
                  AND (:keyword IS NULL OR p.name LIKE :keywordLike OR p.spu_code LIKE :keywordLike OR c.name LIKE :keywordLike)
                GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
                ORDER BY p.id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("keyword", normalizeKeyword(keyword))
                .addValue("keywordLike", likeKeyword(keyword));
        return jdbcTemplate.query(sql, params, productSearchItemRowMapper());
    }

    public Optional<SkuSearchItem> findSku(Long spuId, String color, String size) {
        String sql = """
                SELECT
                    s.id AS sku_id,
                    s.sku_code,
                    s.spu_id,
                    p.name AS product_name,
                    co.name AS color,
                    so.code AS size,
                    s.sale_price,
                    s.status
                FROM product_sku s
                JOIN product_spu p ON p.id = s.spu_id
                JOIN color co ON co.id = s.color_id
                JOIN size_option so ON so.id = s.size_id
                WHERE s.spu_id = :spuId
                  AND co.name = :color
                  AND so.code = :size
                  AND s.status = 'on_sale'
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("spuId", spuId)
                .addValue("color", color)
                .addValue("size", size);
        List<SkuSearchItem> results = jdbcTemplate.query(sql, params, skuSearchItemRowMapper());
        return results.stream().findFirst();
    }

    public Optional<ProductDetail> findProductDetail(Long spuId) {
        String baseSql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.description,
                    p.main_image_url,
                    f.name AS fit_type,
                    MIN(s.sale_price) AS min_price,
                    MAX(s.sale_price) AS max_price
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku s ON s.spu_id = p.id
                WHERE p.id = :spuId
                GROUP BY p.id, p.spu_code, p.name, c.name, p.description, p.main_image_url, f.name
                """;
        MapSqlParameterSource params = new MapSqlParameterSource("spuId", spuId);
        List<ProductDetail> details = jdbcTemplate.query(baseSql, params, (rs, rowNum) -> new ProductDetail(
                rs.getLong("spu_id"),
                rs.getString("spu_code"),
                rs.getString("name"),
                rs.getString("category_name"),
                rs.getString("description"),
                rs.getString("main_image_url"),
                rs.getString("fit_type"),
                findMaterials(spuId),
                findSeasons(spuId),
                findStyleTags(spuId),
                findAttributes(spuId),
                rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price")
        ));
        return details.stream().findFirst();
    }

    public List<RecommendationCandidate> findRecommendationCandidates(
            String category,
            String style,
            String season,
            String material,
            String fit,
            Integer budgetMax
    ) {
        String sql = """
                SELECT
                    p.id AS spu_id,
                    p.spu_code,
                    p.name,
                    c.name AS category_name,
                    p.main_image_url,
                    f.name AS fit_type,
                    GROUP_CONCAT(DISTINCT m.name ORDER BY m.name SEPARATOR ',') AS materials,
                    GROUP_CONCAT(DISTINCT se.code ORDER BY se.code SEPARATOR ',') AS seasons,
                    GROUP_CONCAT(DISTINCT st.code ORDER BY st.code SEPARATOR ',') AS style_tags,
                    MIN(sku.sale_price) AS min_price,
                    MAX(sku.sale_price) AS max_price,
                    COALESCE(SUM(inv.available_stock), 0) AS total_available_stock
                FROM product_spu p
                JOIN category c ON c.id = p.category_id
                LEFT JOIN fit_type f ON f.id = p.fit_type_id
                JOIN product_sku sku ON sku.spu_id = p.id
                LEFT JOIN inventory inv ON inv.sku_id = sku.id
                LEFT JOIN product_material pm ON pm.spu_id = p.id
                LEFT JOIN material m ON m.id = pm.material_id
                LEFT JOIN product_season ps ON ps.spu_id = p.id
                LEFT JOIN season se ON se.id = ps.season_id
                LEFT JOIN product_style_tag pst ON pst.spu_id = p.id
                LEFT JOIN style_tag st ON st.id = pst.style_tag_id
                WHERE p.status = 'on_sale'
                  AND (:category IS NULL OR c.name = :category)
                  AND (:style IS NULL OR st.code = :style)
                  AND (:season IS NULL OR se.code = :season)
                  AND (:material IS NULL OR m.name = :material)
                  AND (:fit IS NULL OR f.code = :fit)
                GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
                HAVING (:budgetMax IS NULL OR MIN(sku.sale_price) <= :budgetMax)
                   AND COALESCE(SUM(inv.available_stock), 0) > 0
                ORDER BY total_available_stock DESC, min_price ASC, p.id ASC
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("category", blankToNull(category))
                .addValue("style", blankToNull(style))
                .addValue("season", blankToNull(season))
                .addValue("material", blankToNull(material))
                .addValue("fit", blankToNull(fit))
                .addValue("budgetMax", budgetMax);
        return jdbcTemplate.query(sql, params, recommendationCandidateRowMapper());
    }

    private List<String> findMaterials(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT m.name
                FROM product_material pm
                JOIN material m ON m.id = pm.material_id
                WHERE pm.spu_id = :spuId
                ORDER BY m.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private List<String> findSeasons(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT se.code
                FROM product_season ps
                JOIN season se ON se.id = ps.season_id
                WHERE ps.spu_id = :spuId
                ORDER BY se.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private List<String> findStyleTags(Long spuId) {
        return jdbcTemplate.queryForList("""
                SELECT st.code
                FROM product_style_tag pst
                JOIN style_tag st ON st.id = pst.style_tag_id
                WHERE pst.spu_id = :spuId
                ORDER BY st.id
                """, new MapSqlParameterSource("spuId", spuId), String.class);
    }

    private Map<String, String> findAttributes(Long spuId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT attr_name, attr_value
                FROM product_attribute
                WHERE spu_id = :spuId
                ORDER BY id
                """, new MapSqlParameterSource("spuId", spuId));
        Map<String, String> attributes = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            attributes.put(String.valueOf(row.get("attr_name")), String.valueOf(row.get("attr_value")));
        }
        return attributes;
    }

    private RowMapper<ProductSearchItem> productSearchItemRowMapper() {
        return (rs, rowNum) -> new ProductSearchItem(
                rs.getLong("spu_id"),
                rs.getString("spu_code"),
                rs.getString("name"),
                rs.getString("category_name"),
                rs.getString("main_image_url"),
                rs.getString("fit_type"),
                rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price")
        );
    }

    private RowMapper<SkuSearchItem> skuSearchItemRowMapper() {
        return (rs, rowNum) -> new SkuSearchItem(
                rs.getLong("sku_id"),
                rs.getString("sku_code"),
                rs.getLong("spu_id"),
                rs.getString("product_name"),
                rs.getString("color"),
                rs.getString("size"),
                rs.getBigDecimal("sale_price"),
                rs.getString("status")
        );
    }

    private RowMapper<RecommendationCandidate> recommendationCandidateRowMapper() {
        return (rs, rowNum) -> new RecommendationCandidate(
                rs.getLong("spu_id"),
                rs.getString("spu_code"),
                rs.getString("name"),
                rs.getString("category_name"),
                rs.getString("main_image_url"),
                rs.getString("fit_type"),
                rs.getString("materials"),
                rs.getString("seasons"),
                rs.getString("style_tags"),
                rs.getBigDecimal("min_price"),
                rs.getBigDecimal("max_price"),
                rs.getInt("total_available_stock")
        );
    }

    private String normalizeKeyword(String keyword) {
        return blankToNull(keyword);
    }

    private String likeKeyword(String keyword) {
        String normalized = blankToNull(keyword);
        return normalized == null ? null : "%" + normalized + "%";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
```

- [ ] **步骤 4: 运行 Repository 测试**

运行：

```powershell
.\mvnw.cmd -q -Dtest=ProductCatalogRepositoryTests test
```

预期：PASS。

- [ ] **步骤 5: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product
git commit -m "feat: add product query repository"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 5。

## 任务 5: 实现库存查询 Repository

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/model/InventoryView.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository/InventoryQueryRepository.java`

- [ ] **步骤 1: 创建库存读模型**

创建 `InventoryView.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.model;

public record InventoryView(
        Long skuId,
        String skuCode,
        Long spuId,
        String productName,
        String color,
        String size,
        Integer availableStock,
        Integer lockedStock,
        Integer soldStock,
        Boolean inStock
) {
}
```

- [ ] **步骤 2: 创建库存 Repository**

创建 `InventoryQueryRepository.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.repository;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class InventoryQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InventoryQueryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<InventoryView> findBySkuId(Long skuId) {
        String sql = """
                SELECT
                    sku.id AS sku_id,
                    sku.sku_code,
                    sku.spu_id,
                    p.name AS product_name,
                    co.name AS color,
                    so.code AS size,
                    inv.available_stock,
                    inv.locked_stock,
                    inv.sold_stock
                FROM inventory inv
                JOIN product_sku sku ON sku.id = inv.sku_id
                JOIN product_spu p ON p.id = sku.spu_id
                JOIN color co ON co.id = sku.color_id
                JOIN size_option so ON so.id = sku.size_id
                WHERE sku.id = :skuId
                """;
        List<InventoryView> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("skuId", skuId),
                (rs, rowNum) -> new InventoryView(
                        rs.getLong("sku_id"),
                        rs.getString("sku_code"),
                        rs.getLong("spu_id"),
                        rs.getString("product_name"),
                        rs.getString("color"),
                        rs.getString("size"),
                        rs.getInt("available_stock"),
                        rs.getInt("locked_stock"),
                        rs.getInt("sold_stock"),
                        rs.getInt("available_stock") > 0
                )
        );
        return results.stream().findFirst();
    }
}
```

- [ ] **步骤 3: 运行现有测试**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：PASS。

- [ ] **步骤 4: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory
git commit -m "feat: add inventory query repository"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 6。

## 任务 6: 增加商品和库存 Service

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service/InventoryQueryService.java`

- [ ] **步骤 1: 创建商品 Service**

创建 `ProductCatalogService.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.repository.ProductQueryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductCatalogService {

    private final ProductQueryRepository productQueryRepository;

    public ProductCatalogService(ProductQueryRepository productQueryRepository) {
        this.productQueryRepository = productQueryRepository;
    }

    public List<ProductSearchItem> searchProducts(String keyword) {
        return productQueryRepository.searchProducts(keyword);
    }

    public ProductDetail getProductDetail(Long spuId) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        return productQueryRepository.findProductDetail(spuId)
                .orElseThrow(() -> new ResourceNotFoundException("product not found: " + spuId));
    }

    public SkuSearchItem findSku(Long spuId, String color, String size) {
        if (spuId == null || spuId <= 0) {
            throw new BadRequestException("spuId must be positive");
        }
        if (color == null || color.isBlank()) {
            throw new BadRequestException("color must not be blank");
        }
        if (size == null || size.isBlank()) {
            throw new BadRequestException("size must not be blank");
        }
        return productQueryRepository.findSku(spuId, color.trim(), size.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("sku not found"));
    }

    public List<RecommendationCandidate> findRecommendationCandidates(
            String category,
            String style,
            String season,
            String material,
            String fit,
            Integer budgetMax
    ) {
        if (budgetMax != null && budgetMax < 0) {
            throw new BadRequestException("budgetMax must not be negative");
        }
        return productQueryRepository.findRecommendationCandidates(category, style, season, material, fit, budgetMax);
    }
}
```

- [ ] **步骤 2: 创建库存 Service**

创建 `InventoryQueryService.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.service;

import com.recommendation.intelligentoutfitrecommendationsystem.common.error.BadRequestException;
import com.recommendation.intelligentoutfitrecommendationsystem.common.error.ResourceNotFoundException;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.repository.InventoryQueryRepository;
import org.springframework.stereotype.Service;

@Service
public class InventoryQueryService {

    private final InventoryQueryRepository inventoryQueryRepository;

    public InventoryQueryService(InventoryQueryRepository inventoryQueryRepository) {
        this.inventoryQueryRepository = inventoryQueryRepository;
    }

    public InventoryView getInventoryBySkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new BadRequestException("skuId must be positive");
        }
        return inventoryQueryRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ResourceNotFoundException("inventory not found for sku: " + skuId));
    }
}
```

- [ ] **步骤 3: 运行测试**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：PASS。

- [ ] **步骤 4: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service
git commit -m "feat: add product inventory services"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 7。

## 任务 7: 增加商品和库存 internal API

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductController.java`
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/api/InternalInventoryController.java`
- 测试：`src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/InternalProductControllerTests.java`
- 测试：`src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InternalInventoryControllerTests.java`

- [ ] **步骤 1: 先写失败的 Controller 测试**

创建 `InternalProductControllerTests.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class InternalProductControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsMissingInternalToken() throws Exception {
        mockMvc.perform(get("/internal/products/search").param("keyword", "T恤"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void searchesProductsWithInternalToken() throws Exception {
        mockMvc.perform(get("/internal/products/search")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("keyword", "T恤"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].spuCode").value("TSHIRT_BASIC_001"));
    }

    @Test
    void findsBlackLargeSku() throws Exception {
        mockMvc.perform(get("/internal/skus/search")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("spuId", "1001")
                        .param("color", "黑色")
                        .param("size", "L"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skuCode").value("TS-BASIC-001-BLK-L"));
    }

    @Test
    void findsCommuteJacketRecommendationCandidate() throws Exception {
        mockMvc.perform(get("/internal/recommendation-candidates")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("category", "外套")
                        .param("style", "commute")
                        .param("season", "autumn")
                        .param("budgetMax", "400"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].spuCode").value("JACKET_COMMUTE_001"));
    }
}
```

创建 `InternalInventoryControllerTests.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class InternalInventoryControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsInventoryBySkuId() throws Exception {
        mockMvc.perform(get("/internal/inventory")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("skuId", "2003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableStock").value(8))
                .andExpect(jsonPath("$.data.inStock").value(true));
    }
}
```

运行：

```powershell
.\mvnw.cmd -q -Dtest=InternalProductControllerTests,InternalInventoryControllerTests test
```

预期：失败，因为 Controller 还不存在。

- [ ] **步骤 2: 创建 internal 商品 Controller**

创建 `InternalProductController.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal")
public class InternalProductController {

    private final ProductCatalogService productCatalogService;

    public InternalProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping("/products/search")
    public ApiResponse<List<ProductSearchItem>> searchProducts(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productCatalogService.searchProducts(keyword));
    }

    @GetMapping("/products/{spuId}")
    public ApiResponse<ProductDetail> getProductDetail(@PathVariable Long spuId) {
        return ApiResponse.ok(productCatalogService.getProductDetail(spuId));
    }

    @GetMapping("/skus/search")
    public ApiResponse<SkuSearchItem> findSku(
            @RequestParam Long spuId,
            @RequestParam String color,
            @RequestParam String size
    ) {
        return ApiResponse.ok(productCatalogService.findSku(spuId, color, size));
    }

    @GetMapping("/recommendation-candidates")
    public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String fit,
            @RequestParam(required = false) Integer budgetMax
    ) {
        return ApiResponse.ok(productCatalogService.findRecommendationCandidates(category, style, season, material, fit, budgetMax));
    }
}
```

- [ ] **步骤 3: 创建 internal 库存 Controller**

创建 `InternalInventoryController.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import com.recommendation.intelligentoutfitrecommendationsystem.inventory.service.InventoryQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalInventoryController {

    private final InventoryQueryService inventoryQueryService;

    public InternalInventoryController(InventoryQueryService inventoryQueryService) {
        this.inventoryQueryService = inventoryQueryService;
    }

    @GetMapping("/inventory")
    public ApiResponse<InventoryView> getInventory(@RequestParam Long skuId) {
        return ApiResponse.ok(inventoryQueryService.getInventoryBySkuId(skuId));
    }
}
```

- [ ] **步骤 4: 运行 internal API 测试**

运行：

```powershell
.\mvnw.cmd -q -Dtest=InternalProductControllerTests,InternalInventoryControllerTests test
```

预期：PASS。

- [ ] **步骤 5: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/api src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory
git commit -m "feat: add internal product inventory apis"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 8。

## 任务 8: 增加公开只读商品 API

**文件：**
- 新建：`src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java`

- [ ] **步骤 1: 创建公开商品 Controller**

创建 `ProductController.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.api;

import com.recommendation.intelligentoutfitrecommendationsystem.common.api.ApiResponse;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.service.ProductCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductCatalogService productCatalogService;

    public ProductController(ProductCatalogService productCatalogService) {
        this.productCatalogService = productCatalogService;
    }

    @GetMapping
    public ApiResponse<List<ProductSearchItem>> searchProducts(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(productCatalogService.searchProducts(keyword));
    }

    @GetMapping("/{spuId}")
    public ApiResponse<ProductDetail> getProductDetail(@PathVariable Long spuId) {
        return ApiResponse.ok(productCatalogService.getProductDetail(spuId));
    }

    @GetMapping("/recommendation-candidates")
    public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String style,
            @RequestParam(required = false) String season,
            @RequestParam(required = false) String material,
            @RequestParam(required = false) String fit,
            @RequestParam(required = false) Integer budgetMax
    ) {
        return ApiResponse.ok(productCatalogService.findRecommendationCandidates(category, style, season, material, fit, budgetMax));
    }
}
```

- [ ] **步骤 2: 运行完整测试**

运行：

```powershell
.\mvnw.cmd -q test
```

预期：PASS。

- [ ] **步骤 3: 手动检查本地公开 API**

启动应用：

```powershell
.\mvnw.cmd spring-boot:run
```

在另一个终端调用：

```powershell
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8080/api/products?keyword=T%E6%81%A4"
```

预期响应包含：

```json
{
  "success": true,
  "data": [
    {
      "spuCode": "TSHIRT_BASIC_001"
    }
  ]
}
```

- [ ] **步骤 4: 手动检查 internal API**

调用：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://127.0.0.1:8080/internal/inventory?skuId=2003" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

预期响应包含：

```json
{
  "success": true,
  "data": {
    "skuCode": "TS-BASIC-001-BLK-L",
    "availableStock": 8,
    "inStock": true
  }
}
```

- [ ] **步骤 5: 阶段检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java
git commit -m "feat: add public product read apis"
```

如果当前工作区不是 Git 仓库，在对话里记录该检查点，然后继续任务 9。

## 任务 9: 第一阶段最终验证

**文件：**
- 验证任务 1-8 创建和修改的全部文件。

- [ ] **步骤 1: 运行完整测试套件**

运行：

```powershell
.\mvnw.cmd test
```

预期：构建成功，所有测试通过。

- [ ] **步骤 2: 检查是否有误留下的临时标记**

运行：

```powershell
rg -n "T[B]D|X[X]X" src docs
```

预期：第一阶段新增文件中没有匹配结果。

- [ ] **步骤 3: 验证 internal token 保护生效**

启动应用后，不带 token 调用：

```powershell
Invoke-WebRequest -Method Get -Uri "http://127.0.0.1:8080/internal/products/search?keyword=T%E6%81%A4"
```

预期：HTTP 401。

带 token 调用：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://127.0.0.1:8080/internal/products/search?keyword=T%E6%81%A4" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

预期：HTTP 200，并返回 `TSHIRT_BASIC_001`。

- [ ] **步骤 4: 验证 Python 后续会调用的查询样例**

运行这些手动检查：

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://127.0.0.1:8080/internal/skus/search?spuId=1001&color=%E9%BB%91%E8%89%B2&size=L" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

预期：`data.skuCode = TS-BASIC-001-BLK-L`。

```powershell
Invoke-RestMethod -Method Get `
  -Uri "http://127.0.0.1:8080/internal/recommendation-candidates?category=%E5%A4%96%E5%A5%97&style=commute&season=autumn&budgetMax=400" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

预期：`data[0].spuCode = JACKET_COMMUTE_001`。

- [ ] **步骤 5: 最终检查点**

如果当前工作区是 Git 仓库，执行：

```powershell
git status --short
git add .
git commit -m "feat: complete product inventory internal api phase"
```

如果当前工作区不是 Git 仓库，在对话里总结完成的文件和测试结果。

## 验收标准

满足以下条件时，第一阶段完成：

- `.\mvnw.cmd test` 通过。
- Flyway 能在 H2 测试环境里创建商品和库存表。
- Demo 服装目录能用固定 ID 稳定初始化。
- 公开商品搜索 API 能返回种子商品数据。
- internal API 在缺少 `X-Internal-Token` 时会拒绝请求。
- internal 商品搜索能找到 `TSHIRT_BASIC_001`。
- internal SKU 查询能找到 `TS-BASIC-001-BLK-L`。
- internal 库存查询对 SKU `2003` 返回 8 件可售库存。
- internal 推荐候选查询能在“通勤、秋季、外套、400 元以内”条件下返回 `JACKET_COMMUTE_001`。

## 第一阶段之后的后续计划

第一阶段验收后，再单独为下面任一阶段写实施计划：

1. 用户资料和穿衣偏好模块。
2. AI 会话和 assistant session 模块。
3. Java assistant-service 调用 Python `/chat`。
4. Python structured lookup 改为调用 Java internal 商品 API。

推荐下一阶段先做用户资料和穿衣偏好模块。原因是 AI 推荐在 Java 调 Python 之前，需要先有用户身高体重、偏好版型、颜色偏好、预算和风格偏好。
