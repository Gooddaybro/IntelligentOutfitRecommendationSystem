# 代码注释规范

本文档用于约束 Intelligent Outfit Recommendation System Java 后端的代码注释风格。

核心原则：注释解释“为什么这样设计”和“这里有什么边界”，不要重复代码已经清楚表达的“做了什么”。

## 1. 总体原则

### 应该写注释的情况

以下情况必须优先考虑补充注释：

- 业务规则不是从代码名本身就能看出来。
- 代码承担了架构边界职责，例如 Java 后端给 Python AI 服务提供 internal API。
- SQL 查询比较复杂，包含多表关联、动态条件、聚合、推荐候选筛选。
- Service 层有非显然的装配逻辑，例如商品详情的材质、季节、风格标签、扩展属性分多次查询后组装。
- 存在兼容性、性能、事务、一致性、安全性等需要后续维护者注意的点。
- 当前实现是阶段性方案，后续会替换或演进，但不能只写 `TODO`，必须说明原因和预期方向。

### 不应该写注释的情况

以下注释一般不写：

```java
// 获取商品详情
public ProductDetail getProductDetail(Long spuId) { ... }
```

原因：方法名已经表达清楚，注释没有提供额外信息。

也不要写这类注释：

```java
// 设置材质
detail.setMaterials(productMapper.findMaterials(spuId));
```

原因：代码本身已经说明了动作，注释只是重复。

## 2. Controller 注释规范

Controller 只需要说明接口归属和使用方，不需要给每个简单接口写注释。

推荐写法：

```java
/**
 * 公开商品接口，供商城前端和本地调试使用。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {
}
```

```java
/**
 * Internal 商品接口，供 Python AI 推荐服务查询商品事实数据。
 *
 * 所有 /internal/** 接口都需要 X-Internal-Token。
 */
@RestController
@RequestMapping("/internal")
public class InternalProductController {
}
```

适合写在 Controller 的信息：

- 公开 API 还是 internal API。
- 主要调用方是谁。
- 是否需要 internal token。
- 接口是否给 AI 服务使用。

不适合写在 Controller 的信息：

- SQL 细节。
- 表结构细节。
- 每个参数的普通 getter/setter 说明。

## 3. Service 注释规范

Service 是业务规则和装配逻辑的核心位置。复杂逻辑前可以写短注释。

推荐写法：

```java
public ProductDetail getProductDetail(Long spuId) {
    ProductDetail detail = productMapper.findProductDetailBase(spuId);
    if (detail == null) {
        throw new ResourceNotFoundException("product not found: " + spuId);
    }

    // 多值属性保持为轻量查询后在 Service 层装配，避免 XML resultMap 过度嵌套。
    detail.setMaterials(productMapper.findMaterials(spuId));
    detail.setSeasons(productMapper.findSeasons(spuId));
    detail.setStyleTags(productMapper.findStyleTags(spuId));
    detail.setAttributes(toAttributesMap(productMapper.findAttributes(spuId)));
    return detail;
}
```

Service 注释重点：

- 为什么校验放在 Service。
- 为什么这里做装配，而不是放在 Mapper XML。
- 为什么抛这个异常。
- 为什么某些参数需要 trim、大小写标准化。
- 这里是否影响 AI 推荐结果。

## 4. Mapper XML 注释规范

Mapper XML 中复杂 SQL 必须写注释。尤其是推荐候选商品查询，因为它直接服务 AI 推荐。

推荐写法：

```xml
<!--
    给 AI 推荐服务提供候选商品池。
    这里只返回有库存、上架、符合用户筛选条件的 SPU，
    复杂解释和排序策略仍由 Python AI 服务完成。
-->
<select id="findRecommendationCandidates" resultType="...RecommendationCandidate">
    ...
</select>
```

Mapper XML 注释重点：

- 查询服务哪个业务场景。
- 动态条件的含义。
- 为什么要过滤无库存商品。
- 为什么按库存、价格、ID 排序。
- 为什么某些字段使用聚合。

不需要给简单 SQL 写冗余注释：

```xml
<!-- 根据 skuId 查询库存 -->
<select id="findBySkuId" ...>
```

如果方法名已经是 `findBySkuId`，这种注释可以省略。

## 5. DTO 注释规范

DTO 用于表达接口入参或跨服务数据边界。推荐在类级别写用途说明。

推荐写法：

```java
/**
 * 推荐候选商品查询参数。
 *
 * 公开 API 和 Python internal API 复用同一个 DTO，避免两个入口的筛选语义不一致。
 */
@Data
public class RecommendationCandidateQuery {
}
```

字段注释只在容易误解时写。例如：

```java
/**
 * 用户预算上限，单位为商品销售价的数据库货币单位。
 */
private Integer budgetMax;
```

普通字段如 `category`、`style`、`season` 不强制写注释。

## 6. Model 注释规范

Model 默认不写字段注释，除非字段存在业务歧义。

可以写注释的例子：

```java
/**
 * 推荐候选商品视图。
 *
 * materials、seasons、styleTags 使用逗号拼接，方便 Python AI 服务快速读取候选摘要。
 * 商品详情接口仍返回 List 形式的结构化属性。
 */
public class RecommendationCandidate {
}
```

不建议对每个字段写：

```java
// 商品 ID
private Long spuId;
```

原因：字段名已经足够清楚。

## 7. 测试注释规范

测试优先通过方法名表达意图，不优先写注释。

推荐测试名：

```java
void findRecommendationCandidatesFiltersByStyleSeasonAndBudget()
```

如果测试包含特殊准备数据或历史回归场景，可以加注释：

```java
// 这个断言保护 Python AI 服务依赖的 JSON 字段名，避免重构时破坏跨服务契约。
mockMvc.perform(...)
        .andExpect(jsonPath("$.data[0].spuCode").value("TSHIRT_BASIC_001"));
```

## 8. TODO 注释规范

允许写 TODO，但必须说明背景和后续方向。

不推荐：

```java
// TODO optimize
```

推荐：

```java
// TODO: 当前候选商品按简单库存和价格排序；接入用户画像后，应迁移到 assistant-service 做个性化排序。
```

## 9. 本项目优先补注释位置

当前阶段优先给这些位置补充注释：

| 文件 | 注释重点 |
|---|---|
| `ProductCatalogService.java` | 商品详情多值属性为什么在 Service 层装配 |
| `ProductMapper.xml` | 推荐候选查询的业务语义、库存过滤、动态筛选条件 |
| `InventoryMapper.xml` | 库存查询给 AI 服务避免推荐无库存 SKU |
| `RecommendationCandidateQuery.java` | 公开 API 和 Python internal API 共用筛选参数 |
| `InternalProductController.java` | Python AI 服务使用的商品事实接口 |
| `InternalInventoryController.java` | Python AI 服务使用的库存事实接口 |

## 10. 检查清单

每次补注释或代码评审时，按下面清单检查：

- 注释是否解释了代码无法直接表达的原因或边界？
- 注释是否会随着代码变化而容易过期？
- 注释是否重复了方法名、变量名、SQL 名称？
- 注释是否说明了 Java 后端和 Python AI 服务之间的契约？
- Mapper XML 中复杂 SQL 是否说明了业务目的？
- Service 中复杂装配是否说明了为什么不放在 XML 中？

