# MyBatis 数据访问层重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将商品目录和库存查询模块从 `NamedParameterJdbcTemplate` Repository 重构为 MyBatis Mapper 接口 + XML SQL 映射，同时保持现有 API JSON 响应结构和测试行为不变。

**Architecture:** 目标调用链为 `Controller -> Service -> Mapper Interface -> Mapper XML -> Database`。Service 层负责业务校验和复杂字段装配，Mapper XML 只负责清晰、可维护的 SQL 查询。商品详情中的材质、季节、风格标签和扩展属性由 Service 调用轻量级 Mapper 方法后组装，避免复杂嵌套 `resultMap`。

**Tech Stack:** Java 21, Spring Boot 4.0.6, MyBatis Spring Boot Starter 4.0.0, Lombok, Flyway, MySQL 8.0, H2, JUnit 5, MockMvc.

---

## 设计决策

1. MyBatis 版本使用 `org.mybatis.spring.boot:mybatis-spring-boot-starter:4.0.0`。当前项目是 Spring Boot 4.0.6，MyBatis 官方兼容表中 4.0 系列对应 Spring Boot 4.0+ 和 Java 17+。
2. XML 文件放在 `src/main/resources/mapper/product/ProductMapper.xml` 和 `src/main/resources/mapper/inventory/InventoryMapper.xml`。
3. Mapper 接口放在 `product/mapper` 和 `inventory/mapper` 包。
4. 查询返回模型从 Java `record` 改成 Lombok JavaBean，使用 `@Data`、`@NoArgsConstructor`、`@AllArgsConstructor`，保证 MyBatis setter 映射稳定。
5. GET 查询参数 DTO 使用 Lombok JavaBean，而不是 record。这样 Spring MVC query 参数绑定和 MyBatis XML 属性读取都更稳定。
6. `ProductDetail` 的基础字段由 `findProductDetailBase` 查询，集合字段由 `findMaterials`、`findSeasons`、`findStyleTags`、`findAttributes` 查询后在 Service 层组装。
7. 删除 `ProductQueryRepository` 和 `InventoryQueryRepository`，Service 直接调用 MyBatis Mapper。
8. 重构前后 API JSON 字段名保持不变，例如 `spuCode`、`availableStock`、`inStock`。

## 文件结构

本次重构会创建、修改或删除这些文件：

```text
pom.xml
src/main/resources/application.properties
src/test/resources/application-test.properties

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto/RecommendationCandidateQuery.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java
src/main/resources/mapper/product/ProductMapper.xml

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/mapper/InventoryMapper.java
src/main/resources/mapper/inventory/InventoryMapper.xml

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductSearchItem.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductDetail.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/SkuSearchItem.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidate.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductAttributeItem.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/model/InventoryView.java

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service/InventoryQueryService.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductController.java

src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryMapperTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductControllerTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/InternalProductControllerTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryServiceTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InternalInventoryControllerTests.java

src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository/ProductQueryRepository.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository/InventoryQueryRepository.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogRepositoryTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryRepositoryTests.java
```

## Task 1: 引入 MyBatis 依赖和配置

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/resources/application.properties`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: 修改 Maven 依赖**

在 `pom.xml` 的 `<dependencies>` 中加入 MyBatis starter：

```xml
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>4.0.0</version>
</dependency>
```

保留已有的：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-flyway</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <scope>runtime</scope>
</dependency>
```

原因：MyBatis 仍然使用 Spring Boot 的 `DataSource`，Flyway 仍然负责数据库迁移。

- [ ] **Step 2: 添加 MyBatis 配置**

在 `src/main/resources/application.properties` 和 `src/test/resources/application-test.properties` 中都加入：

```properties
mybatis.mapper-locations=classpath:mapper/**/*.xml
mybatis.configuration.map-underscore-to-camel-case=true
```

- [ ] **Step 3: 运行基础编译测试**

运行：

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q test
```

Expected: 现有测试仍然通过，因为业务代码还没有切到 MyBatis。

- [ ] **Step 4: 提交检查点**

```powershell
git add pom.xml src/main/resources/application.properties src/test/resources/application-test.properties
git commit -m "chore: add mybatis configuration"
```

## Task 2: 将查询返回模型改成 Lombok JavaBean

**Files:**
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductSearchItem.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/SkuSearchItem.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidate.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductDetail.java`
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/ProductAttributeItem.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/model/InventoryView.java`

- [ ] **Step 1: 改造 `ProductSearchItem`**

将 record 改为：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchItem {
    private Long spuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String mainImageUrl;
    private String fitType;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
```

- [ ] **Step 2: 改造 `SkuSearchItem`**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkuSearchItem {
    private Long skuId;
    private String skuCode;
    private Long spuId;
    private String productName;
    private String color;
    private String size;
    private BigDecimal salePrice;
    private String status;
}
```

- [ ] **Step 3: 改造 `RecommendationCandidate`**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidate {
    private Long spuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String mainImageUrl;
    private String fitType;
    private String materials;
    private String seasons;
    private String styleTags;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private Integer totalAvailableStock;
}
```

- [ ] **Step 4: 改造 `ProductDetail`**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetail {
    private Long spuId;
    private String spuCode;
    private String name;
    private String categoryName;
    private String description;
    private String mainImageUrl;
    private String fitType;
    private List<String> materials;
    private List<String> seasons;
    private List<String> styleTags;
    private Map<String, String> attributes;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
}
```

- [ ] **Step 5: 新建 `ProductAttributeItem`**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttributeItem {
    private String attrName;
    private String attrValue;
}
```

- [ ] **Step 6: 改造 `InventoryView`**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryView {
    private Long skuId;
    private String skuCode;
    private Long spuId;
    private String productName;
    private String color;
    private String size;
    private Integer availableStock;
    private Integer lockedStock;
    private Integer soldStock;
    private Boolean inStock;
}
```

- [ ] **Step 7: 更新 JavaBean accessor 使用方式**

将代码和测试中的 record accessor 改成 getter：

```text
sku.skuCode() -> sku.getSkuCode()
sku.salePrice() -> sku.getSalePrice()
inventory.availableStock() -> inventory.getAvailableStock()
inventory.inStock() -> inventory.getInStock()
```

Repository 中的构造器调用可以暂时保留，因为类有 `@AllArgsConstructor`。

- [ ] **Step 8: 运行测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q test
```

Expected: 所有测试通过，API JSON 字段名不变。

- [ ] **Step 9: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/model src/test/java
git commit -m "refactor: convert query models to lombok beans"
```

## Task 3: 引入推荐候选查询 DTO

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto/RecommendationCandidateQuery.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/ProductController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductController.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`

- [ ] **Step 1: 新建 Query DTO**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationCandidateQuery {
    private String category;
    private String style;
    private String season;
    private String material;
    private String fit;
    private Integer budgetMax;
}
```

- [ ] **Step 2: 修改 Service 方法签名**

`ProductCatalogService` 中将：

```java
public List<RecommendationCandidate> findRecommendationCandidates(
        String category,
        String style,
        String season,
        String material,
        String fit,
        Integer budgetMax
)
```

改为：

```java
public List<RecommendationCandidate> findRecommendationCandidates(RecommendationCandidateQuery query)
```

校验逻辑改为：

```java
if (query.getBudgetMax() != null && query.getBudgetMax() < 0) {
    throw new BadRequestException("budgetMax must not be negative");
}
```

此任务阶段仍然调用旧 Repository：

```java
return productQueryRepository.findRecommendationCandidates(
        query.getCategory(),
        query.getStyle(),
        query.getSeason(),
        query.getMaterial(),
        query.getFit(),
        query.getBudgetMax()
);
```

- [ ] **Step 3: 修改公开 Controller**

`ProductController` 中：

```java
@GetMapping("/recommendation-candidates")
public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(RecommendationCandidateQuery query) {
    return ApiResponse.ok(productCatalogService.findRecommendationCandidates(query));
}
```

- [ ] **Step 4: 修改 internal Controller**

`InternalProductController` 中：

```java
@GetMapping("/recommendation-candidates")
public ApiResponse<List<RecommendationCandidate>> findRecommendationCandidates(RecommendationCandidateQuery query) {
    return ApiResponse.ok(productCatalogService.findRecommendationCandidates(query));
}
```

- [ ] **Step 5: 更新 Service 测试**

如果 `ProductCatalogServiceTests` 中直接调用推荐候选方法，改为：

```java
new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400)
```

- [ ] **Step 6: 运行 Controller 测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=ProductControllerTests,InternalProductControllerTests' test
```

Expected: 推荐候选接口仍然返回 `JACKET_COMMUTE_001`。

- [ ] **Step 7: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product
git commit -m "refactor: add recommendation query dto"
```

## Task 4: 创建 ProductMapper 和 ProductMapper.xml

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- Create: `src/main/resources/mapper/product/ProductMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`

- [ ] **Step 1: 先写 Mapper 测试**

创建 `ProductCatalogMapperTests.java`：

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ProductCatalogMapperTests {

    @Autowired
    private ProductMapper mapper;

    @Test
    void searchProductsFindsBasicTshirtByKeyword() {
        var results = mapper.searchProducts("TSHIRT_BASIC_001");

        assertThat(results).extracting("spuCode").contains("TSHIRT_BASIC_001");
    }

    @Test
    void findSkuReturnsBlackLargeTshirt() {
        var sku = mapper.findSku(1001L, "黑色", "L");

        assertThat(sku.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(sku.getSalePrice().intValue()).isEqualTo(99);
    }

    @Test
    void findProductDetailBaseReturnsBasicFields() {
        var detail = mapper.findProductDetailBase(1001L);

        assertThat(detail.getSpuCode()).isEqualTo("TSHIRT_BASIC_001");
        assertThat(detail.getName()).contains("T恤");
    }

    @Test
    void findsProductMultiValueAttributes() {
        assertThat(mapper.findMaterials(1001L)).contains("纯棉");
        assertThat(mapper.findSeasons(1001L)).contains("summer");
        assertThat(mapper.findStyleTags(1001L)).contains("casual");
        assertThat(mapper.findAttributes(1001L)).extracting("attrName").contains("厚薄");
    }

    @Test
    void findRecommendationCandidatesFiltersByStyleSeasonAndBudget() {
        var query = new RecommendationCandidateQuery("外套", "commute", "autumn", null, null, 400);

        var candidates = mapper.findRecommendationCandidates(query);

        assertThat(candidates).extracting("spuCode").contains("JACKET_COMMUTE_001");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=ProductCatalogMapperTests' test
```

Expected: FAIL，原因是 `ProductMapper` 尚未创建。

- [ ] **Step 3: 创建 ProductMapper 接口**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.product.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.product.dto.RecommendationCandidateQuery;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate;
import com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductMapper {

    List<ProductSearchItem> searchProducts(@Param("keyword") String keyword);

    ProductDetail findProductDetailBase(@Param("spuId") Long spuId);

    SkuSearchItem findSku(
            @Param("spuId") Long spuId,
            @Param("color") String color,
            @Param("size") String size
    );

    List<RecommendationCandidate> findRecommendationCandidates(RecommendationCandidateQuery query);

    List<String> findMaterials(@Param("spuId") Long spuId);

    List<String> findSeasons(@Param("spuId") Long spuId);

    List<String> findStyleTags(@Param("spuId") Long spuId);

    List<ProductAttributeItem> findAttributes(@Param("spuId") Long spuId);
}
```

- [ ] **Step 4: 创建 ProductMapper.xml**

创建 `src/main/resources/mapper/product/ProductMapper.xml`，namespace 必须等于接口全限定名：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.recommendation.intelligentoutfitrecommendationsystem.product.mapper.ProductMapper">

    <select id="searchProducts" resultType="com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductSearchItem">
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
        <if test="keyword != null and keyword != ''">
            AND (
                p.name LIKE CONCAT('%', #{keyword}, '%')
                OR p.spu_code LIKE CONCAT('%', #{keyword}, '%')
                OR c.name LIKE CONCAT('%', #{keyword}, '%')
            )
        </if>
        GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
        ORDER BY p.id
    </select>

    <select id="findProductDetailBase" resultType="com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductDetail">
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
        WHERE p.id = #{spuId}
        GROUP BY p.id, p.spu_code, p.name, c.name, p.description, p.main_image_url, f.name
    </select>

    <select id="findSku" resultType="com.recommendation.intelligentoutfitrecommendationsystem.product.model.SkuSearchItem">
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
        WHERE s.spu_id = #{spuId}
          AND co.name = #{color}
          AND so.code = #{size}
          AND s.status = 'on_sale'
    </select>

    <select id="findRecommendationCandidates" resultType="com.recommendation.intelligentoutfitrecommendationsystem.product.model.RecommendationCandidate">
        SELECT
            p.id AS spu_id,
            p.spu_code,
            p.name,
            c.name AS category_name,
            p.main_image_url,
            f.name AS fit_type,
            MIN(sku.sale_price) AS min_price,
            MAX(sku.sale_price) AS max_price,
            COALESCE(SUM(inv.available_stock), 0) AS total_available_stock
        FROM product_spu p
        JOIN category c ON c.id = p.category_id
        LEFT JOIN fit_type f ON f.id = p.fit_type_id
        JOIN product_sku sku ON sku.spu_id = p.id
        LEFT JOIN inventory inv ON inv.sku_id = sku.id
        WHERE p.status = 'on_sale'
        <if test="category != null and category != ''">
            AND c.name = #{category}
        </if>
        <if test="fit != null and fit != ''">
            AND f.code = #{fit}
        </if>
        <if test="style != null and style != ''">
            AND EXISTS (
                SELECT 1
                FROM product_style_tag pst
                JOIN style_tag st ON st.id = pst.style_tag_id
                WHERE pst.spu_id = p.id AND st.code = #{style}
            )
        </if>
        <if test="season != null and season != ''">
            AND EXISTS (
                SELECT 1
                FROM product_season ps
                JOIN season se ON se.id = ps.season_id
                WHERE ps.spu_id = p.id AND se.code = #{season}
            )
        </if>
        <if test="material != null and material != ''">
            AND EXISTS (
                SELECT 1
                FROM product_material pm
                JOIN material m ON m.id = pm.material_id
                WHERE pm.spu_id = p.id AND m.name = #{material}
            )
        </if>
        GROUP BY p.id, p.spu_code, p.name, c.name, p.main_image_url, f.name
        HAVING COALESCE(SUM(inv.available_stock), 0) &gt; 0
        <if test="budgetMax != null">
            AND MIN(sku.sale_price) &lt;= #{budgetMax}
        </if>
        ORDER BY total_available_stock DESC, min_price ASC, p.id ASC
    </select>

    <select id="findMaterials" resultType="java.lang.String">
        SELECT m.name
        FROM product_material pm
        JOIN material m ON m.id = pm.material_id
        WHERE pm.spu_id = #{spuId}
        ORDER BY m.id
    </select>

    <select id="findSeasons" resultType="java.lang.String">
        SELECT se.code
        FROM product_season ps
        JOIN season se ON se.id = ps.season_id
        WHERE ps.spu_id = #{spuId}
        ORDER BY se.id
    </select>

    <select id="findStyleTags" resultType="java.lang.String">
        SELECT st.code
        FROM product_style_tag pst
        JOIN style_tag st ON st.id = pst.style_tag_id
        WHERE pst.spu_id = #{spuId}
        ORDER BY st.id
    </select>

    <select id="findAttributes" resultType="com.recommendation.intelligentoutfitrecommendationsystem.product.model.ProductAttributeItem">
        SELECT attr_name, attr_value
        FROM product_attribute
        WHERE spu_id = #{spuId}
        ORDER BY id
    </select>

</mapper>
```

- [ ] **Step 5: 运行 ProductMapper 测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=ProductCatalogMapperTests' test
```

Expected: PASS。

- [ ] **Step 6: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper src/main/resources/mapper/product src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java
git commit -m "feat: add product mybatis mapper"
```

## Task 5: 创建 InventoryMapper 和 InventoryMapper.xml

**Files:**
- Create: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/mapper/InventoryMapper.java`
- Create: `src/main/resources/mapper/inventory/InventoryMapper.xml`
- Create: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryMapperTests.java`

- [ ] **Step 1: 先写 Mapper 测试**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class InventoryMapperTests {

    @Autowired
    private InventoryMapper mapper;

    @Test
    void findBySkuIdReturnsAvailableStock() {
        var inventory = mapper.findBySkuId(2003L);

        assertThat(inventory.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
        assertThat(inventory.getAvailableStock()).isEqualTo(8);
        assertThat(inventory.getInStock()).isTrue();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=InventoryMapperTests' test
```

Expected: FAIL，原因是 `InventoryMapper` 尚未创建。

- [ ] **Step 3: 创建 InventoryMapper 接口**

```java
package com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper;

import com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryMapper {

    InventoryView findBySkuId(@Param("skuId") Long skuId);
}
```

- [ ] **Step 4: 创建 InventoryMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.recommendation.intelligentoutfitrecommendationsystem.inventory.mapper.InventoryMapper">

    <select id="findBySkuId" resultType="com.recommendation.intelligentoutfitrecommendationsystem.inventory.model.InventoryView">
        SELECT
            sku.id AS sku_id,
            sku.sku_code,
            sku.spu_id,
            p.name AS product_name,
            co.name AS color,
            so.code AS size,
            inv.available_stock,
            inv.locked_stock,
            inv.sold_stock,
            CASE WHEN inv.available_stock &gt; 0 THEN TRUE ELSE FALSE END AS in_stock
        FROM inventory inv
        JOIN product_sku sku ON sku.id = inv.sku_id
        JOIN product_spu p ON p.id = sku.spu_id
        JOIN color co ON co.id = sku.color_id
        JOIN size_option so ON so.id = sku.size_id
        WHERE sku.id = #{skuId}
    </select>

</mapper>
```

- [ ] **Step 5: 运行 InventoryMapper 测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=InventoryMapperTests' test
```

Expected: PASS。

- [ ] **Step 6: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/mapper src/main/resources/mapper/inventory src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryMapperTests.java
git commit -m "feat: add inventory mybatis mapper"
```

## Task 6: Service 层切换到 MyBatis Mapper

**Files:**
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service/InventoryQueryService.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`
- Modify: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryServiceTests.java`

- [ ] **Step 1: 修改 ProductCatalogService 注入**

将依赖从：

```java
private final ProductQueryRepository productQueryRepository;
```

改为：

```java
private final ProductMapper productMapper;
```

- [ ] **Step 2: 装配商品详情**

`getProductDetail` 中使用：

```java
ProductDetail detail = productMapper.findProductDetailBase(spuId);
if (detail == null) {
    throw new ResourceNotFoundException("product not found: " + spuId);
}
detail.setMaterials(productMapper.findMaterials(spuId));
detail.setSeasons(productMapper.findSeasons(spuId));
detail.setStyleTags(productMapper.findStyleTags(spuId));
detail.setAttributes(toAttributesMap(productMapper.findAttributes(spuId)));
return detail;
```

在 Service 中新增私有方法：

```java
private Map<String, String> toAttributesMap(List<ProductAttributeItem> attributes) {
    Map<String, String> result = new LinkedHashMap<>();
    for (ProductAttributeItem attribute : attributes) {
        result.put(attribute.getAttrName(), attribute.getAttrValue());
    }
    return result;
}
```

- [ ] **Step 3: 改造 SKU 查询**

```java
SkuSearchItem sku = productMapper.findSku(spuId, color.trim(), size.trim().toUpperCase());
if (sku == null) {
    throw new ResourceNotFoundException("sku not found");
}
return sku;
```

- [ ] **Step 4: 改造推荐候选查询**

```java
if (query.getBudgetMax() != null && query.getBudgetMax() < 0) {
    throw new BadRequestException("budgetMax must not be negative");
}
return productMapper.findRecommendationCandidates(query);
```

- [ ] **Step 5: 修改 InventoryQueryService**

```java
InventoryView inventory = inventoryMapper.findBySkuId(skuId);
if (inventory == null) {
    throw new ResourceNotFoundException("inventory not found for sku: " + skuId);
}
return inventory;
```

- [ ] **Step 6: 更新 Service 测试 Mock**

`ProductCatalogServiceTests` 使用 `ProductMapper` 作为 `@Mock`，并将 accessor 改成 getter：

```java
assertThat(sku.getSkuCode()).isEqualTo("TS-BASIC-001-BLK-L");
```

`InventoryQueryServiceTests` 使用 `InventoryMapper` 作为 `@Mock`：

```java
when(inventoryMapper.findBySkuId(2003L))
        .thenReturn(new InventoryView(2003L, "TS-BASIC-001-BLK-L", 1001L, "基础款纯棉T恤", "黑色", "L", 8, 0, 0, true));
```

- [ ] **Step 7: 运行 Service 测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=ProductCatalogServiceTests,InventoryQueryServiceTests' test
```

Expected: PASS。

- [ ] **Step 8: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/service src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryServiceTests.java
git commit -m "refactor: switch services to mybatis mappers"
```

## Task 7: 删除旧 Repository 和旧 Repository 测试

**Files:**
- Delete: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository/ProductQueryRepository.java`
- Delete: `src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository/InventoryQueryRepository.java`
- Delete: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogRepositoryTests.java`
- Delete: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryRepositoryTests.java`

- [ ] **Step 1: 搜索旧 Repository 引用**

```powershell
rg -n "ProductQueryRepository|InventoryQueryRepository" src/main/java src/test/java
```

Expected: 只剩旧 Repository 文件和旧 Repository 测试文件本身。

- [ ] **Step 2: 删除旧文件**

删除：

```text
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository/ProductQueryRepository.java
src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository/InventoryQueryRepository.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogRepositoryTests.java
src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InventoryQueryRepositoryTests.java
```

- [ ] **Step 3: 再次搜索确认**

```powershell
rg -n "ProductQueryRepository|InventoryQueryRepository" src/main/java src/test/java
```

Expected: 无输出。

- [ ] **Step 4: 运行完整测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q test
```

Expected: PASS，测试数量仍覆盖 mapper、service、controller。

- [ ] **Step 5: 提交检查点**

```powershell
git add src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/repository src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/repository src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory
git commit -m "refactor: remove jdbc query repositories"
```

## Task 8: 验证 API JSON 结构保持一致

**Files:**
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductControllerTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/InternalProductControllerTests.java`
- Test: `src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory/InternalInventoryControllerTests.java`

- [ ] **Step 1: 运行 Controller 测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q '-Dtest=ProductControllerTests,InternalProductControllerTests,InternalInventoryControllerTests' test
```

Expected: PASS。

- [ ] **Step 2: 确认 JSON 字段断言覆盖关键字段**

测试中必须继续断言：

```text
$.data[0].spuCode
$.data.spuCode
$.data.styleTags
$.data.skuCode
$.data.availableStock
$.data.inStock
$.data[*].spuCode
```

- [ ] **Step 3: 补充缺失断言**

如果缺少库存字段断言，在 `InternalInventoryControllerTests` 中保留：

```java
.andExpect(jsonPath("$.data.skuCode").value("TS-BASIC-001-BLK-L"))
.andExpect(jsonPath("$.data.availableStock").value(8))
.andExpect(jsonPath("$.data.inStock").value(true));
```

- [ ] **Step 4: 提交检查点**

如果测试文件有新增断言：

```powershell
git add src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/inventory
git commit -m "test: verify api json compatibility"
```

如果没有新增断言，不创建提交。

## Task 9: MySQL 8.0 真实运行验证

**Files:**
- Verify: `src/main/resources/application.properties`
- Verify: `src/main/resources/mapper/product/ProductMapper.xml`
- Verify: `src/main/resources/mapper/inventory/InventoryMapper.xml`

- [ ] **Step 1: 确认本地 MySQL 8.0 配置**

`application.properties` 应指向 MySQL 8.0：

```properties
spring.datasource.url=jdbc:mysql://localhost:3307/intelligent_outfit?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=123456
spring.flyway.enabled=true
```

- [ ] **Step 2: 打包应用**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -q -DskipTests package
```

Expected: BUILD SUCCESS。

- [ ] **Step 3: 启动应用**

```powershell
java -jar target\IntelligentOutfitRecommendationSystem-0.0.1-SNAPSHOT.jar --server.port=18080
```

Expected: 日志包含 `Tomcat started on port 18080`。

- [ ] **Step 4: 调用公开接口**

```powershell
Invoke-RestMethod "http://127.0.0.1:18080/api/products?keyword=TSHIRT_BASIC_001"
```

Expected: 响应中包含：

```text
success = True
data[0].spuCode = TSHIRT_BASIC_001
```

- [ ] **Step 5: 调用 internal 库存接口**

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:18080/internal/inventory?skuId=2003" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

Expected: 响应中包含：

```text
data.skuCode = TS-BASIC-001-BLK-L
data.availableStock = 8
data.inStock = True
```

- [ ] **Step 6: 调用 internal 推荐候选接口**

```powershell
Invoke-RestMethod `
  -Uri "http://127.0.0.1:18080/internal/recommendation-candidates?category=%E5%A4%96%E5%A5%97&style=commute&season=autumn&budgetMax=400" `
  -Headers @{"X-Internal-Token"="dev-internal-token"}
```

Expected: 响应中包含：

```text
data[0].spuCode = JACKET_COMMUTE_001
```

- [ ] **Step 7: 停止应用进程**

停止本次手动启动的 Java 进程，确认没有后台测试服务残留。

## Task 10: 更新功能对照文档

**Files:**
- Modify: `docs/backend-feature-mapping.md`

- [ ] **Step 1: 更新数据访问层描述**

将文档中关于 Repository 的描述改为 MyBatis Mapper：

```text
Mapper Interface 定义数据库访问方法，Mapper XML 保存 SQL。
Service 层负责业务校验和复杂字段装配。
```

- [ ] **Step 2: 更新模块与代码位置**

在 product 模块中加入：

```text
product/mapper/ProductMapper.java
src/main/resources/mapper/product/ProductMapper.xml
```

在 inventory 模块中加入：

```text
inventory/mapper/InventoryMapper.java
src/main/resources/mapper/inventory/InventoryMapper.xml
```

- [ ] **Step 3: 更新测试对照**

将：

```text
ProductCatalogRepositoryTests
InventoryQueryRepositoryTests
```

替换为：

```text
ProductCatalogMapperTests
InventoryMapperTests
```

- [ ] **Step 4: 提交检查点**

```powershell
git add docs/backend-feature-mapping.md
git commit -m "docs: update backend mapping for mybatis"
```

## Task 11: 最终验证

**Files:**
- Verify all modified files.

- [ ] **Step 1: 运行完整测试**

```powershell
$env:JAVA_HOME='D:\Program Files\Java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd test
```

Expected: 所有测试通过。

- [ ] **Step 2: 扫描旧 JDBC Repository 引用**

```powershell
rg -n "NamedParameterJdbcTemplate|ProductQueryRepository|InventoryQueryRepository" src/main/java src/test/java
```

Expected: 无输出。

- [ ] **Step 3: 扫描 MyBatis 配置**

```powershell
rg -n "mybatis|ProductMapper|InventoryMapper" pom.xml src/main/resources src/main/java src/test/java
```

Expected: 输出包含：

```text
mybatis-spring-boot-starter
mybatis.mapper-locations
ProductMapper.java
ProductMapper.xml
InventoryMapper.java
InventoryMapper.xml
```

- [ ] **Step 4: 检查工作区**

```powershell
git status --short
```

Expected: 只剩用户明确保留的文档或本地配置改动。

## 验收标准

本计划完成后需要满足：

- `.\mvnw.cmd test` 通过。
- `ProductQueryRepository` 和 `InventoryQueryRepository` 已删除。
- Service 层只依赖 MyBatis Mapper，不再依赖 JDBC Repository。
- Mapper SQL 全部位于 XML 文件中。
- 商品搜索、商品详情、SKU 查询、库存查询、推荐候选查询行为不变。
- API JSON 字段名不变。
- MySQL 8.0 启动验证通过。
- H2 测试环境验证通过。

## 风险与处理方式

| 风险 | 处理方式 |
|---|---|
| MyBatis XML 在 H2 与 MySQL 8.0 行为不一致 | SQL 避免使用 MySQL 独有聚合语法，集合字段继续由 Service 多次轻量查询装配 |
| JavaBean 改造导致测试 accessor 失败 | 全量替换 record accessor 为 getter |
| JSON 字段名变化 | Controller 测试继续断言 `spuCode`、`availableStock`、`inStock` 等字段 |
| Mapper XML 路径未加载 | 在 `application.properties` 和 `application-test.properties` 同时配置 `mybatis.mapper-locations=classpath:mapper/**/*.xml` |
| 推荐候选查询 DTO 绑定失败 | 使用 Lombok JavaBean DTO，保留无参构造和 setter |
