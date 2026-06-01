# Java 后端工程代码注释与自动化配置规范

本文档用于约束 Intelligent Outfit Recommendation System Java 后端的代码注释风格，并说明如何通过 IDE 模板、Maven Checkstyle 和 CI 把规范落到日常开发流程中。

核心原则：注释解释“为什么这样设计”和“这里有什么边界”，不要重复代码已经清楚表达的“做了什么”。

## 1. 落地链路

本项目采用三层约束：

- 文档定义共识：本文件说明哪些注释必须写、哪些注释不应该写。
- 模板降低成本：通过 IntelliJ IDEA File Templates 和 Live Templates 快速生成类级和方法级 Javadoc。
- 插件强制拦截：通过 Maven Checkstyle 和 GitHub Actions 在 `verify` 阶段阻止基础注释规范违规进入主分支。

自动化检查不能判断注释是否真正解释了业务原因，因此工具只拦截可确定的硬规则。注释质量仍需要开发者和代码评审按本文档判断。

## 2. 总体原则

### 2.1 应该写注释的情况

以下情况必须优先补充注释：

- 业务规则不是从类名、方法名、变量名本身就能看出来。
- 代码承担架构边界职责，例如 Java 后端给 Python AI 服务提供 internal API。
- SQL 查询比较复杂，包含多表关联、动态条件、聚合、推荐候选筛选。
- Service 层有非显然的装配逻辑，例如商品详情的材质、季节、风格标签、扩展属性分多次查询后组装。
- 存在兼容性、性能、事务、一致性、安全性等需要后续维护者注意的点。
- 当前实现是阶段性方案，后续会替换或演进，但不能只写 `TODO`，必须说明原因和预期方向。

### 2.2 不应该写注释的情况

不要写只重复代码含义的注释：

```java
// 获取商品详情
public ProductDetail getProductDetail(Long spuId) {
    ...
}
```

原因：方法名已经表达清楚，注释没有提供额外信息。

也不要写这类注释：

```java
// 设置材质
detail.setMaterials(productMapper.findMaterials(spuId));
```

原因：代码本身已经说明动作，注释只是重复。

## 3. 类与接口注释

`src/main/java` 下所有新建的公开类、接口、枚举、注解、record 必须包含类级 Javadoc。

推荐模板：

```java
/**
 * <p>
 * [请在此处简述该类型的核心职责、业务边界或跨服务契约。]
 * </p>
 *
 * @author Jinyang Xu
 * @date 2026-05-29 14:30
 * @version 1.0.0
 */
public class Example {
}
```

类级注释应该说明：

- 这个类型负责哪个业务边界。
- 是否服务公开 API、internal API、Python AI 服务或数据库映射。
- 是否存在跨服务契约、鉴权边界、数据一致性或兼容性要求。

## 4. 方法注释

不是所有方法都需要 Javadoc。以下方法必须写：

- Service 接口和核心业务方法。
- Controller 对外暴露 API 中语义不明显的方法。
- Mapper 接口中复杂 SQL 的入口方法。
- 内部复杂的 public/private helper，尤其是包含边界条件、异常转换、跨服务数据组装、性能取舍的方法。

推荐模板：

```java
/**
 * 解析并聚合指定设备的时空轨迹数据，生成离散热力图矩阵。
 * <p>
 * 算法复杂度近似为 O(N * M)。为了防止内存溢出，单次最大处理节点数限制为 10,000。
 * </p>
 *
 * @param deviceId 设备唯一标识，不能为 null 且必须大于 0
 * @param startTime 轨迹计算的起始时间戳，单位：毫秒
 * @return 包含空间坐标及频次权重的映射集；若无数据，返回空 Map，不返回 null
 * @throws IllegalArgumentException 当传入非法设备 ID 或时间范围倒置时抛出
 */
public Map<String, Object> processTrajectoryHeatmap(Long deviceId, Long startTime) {
    ...
}
```

方法注释优先说明：

- 入参约束。
- 空值、空集合、异常等边界行为。
- 为什么在这里做校验、装配、过滤、降级或异常转换。
- 是否影响 Python AI 服务、前端 API 或数据库契约。

## 5. Controller 注释规范

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

## 6. Service 注释规范

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

## 7. Mapper XML 注释规范

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

## 8. DTO 和 Model 注释规范

DTO 用于表达接口入参或跨服务数据边界。推荐在类级别写用途说明。

```java
/**
 * 推荐候选商品查询参数。
 *
 * 公开 API 和 Python internal API 复用同一个 DTO，避免两个入口的筛选语义不一致。
 */
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

Model 默认不写字段注释，除非字段存在业务歧义。

## 9. 行内注释与特殊标记

行内注释必须放在被注释代码的上方独占一行，使用 `// ` 开头。禁止在行尾写尾随注释。

推荐：

```java
// requestId 需要先进入 MDC，后续 internal 鉴权失败时日志也能带上同一个链路标识。
registry.addInterceptor(mdcRequestIdInterceptor);
```

禁止：

```java
registry.addInterceptor(mdcRequestIdInterceptor); // 添加链路追踪拦截器
```

TODO 格式：

```java
// TODO: [Jinyang Xu] 接入用户画像后迁移到 assistant-service 做个性化排序 (完成画像权重模型后)
```

FIXME 格式：

```java
// FIXME: 当前 token 过期异常未区分撤销和自然过期，可能导致客户端错误提示不准确。
```

禁止保留大段被注释的废弃代码，改用 Git 历史版本追踪。

## 10. IntelliJ IDEA 自动化配置

### 10.1 新建类自动生成头部注释

配置路径：`Settings/Preferences -> Editor -> File and Code Templates -> Files -> Class / Interface / Enum / Record`。

在类声明上方粘贴：

```java
/**
 * <p>
 * [请在此处简述该类型的核心职责、业务边界或跨服务契约。]
 * </p>
 *
 * @author Jinyang Xu
 * @date ${YEAR}-${MONTH}-${DAY} ${TIME}
 * @version 1.0.0
 */
```

### 10.2 方法快捷键动态注释

配置路径：`Settings/Preferences -> Editor -> Live Templates`。

新建模板组：`CustomGroup`。

新建 Live Template：

- Abbreviation：`*`
- Description：`自动生成标准 Jinyang 风格方法注释`
- Applicable context：`Java -> Java Comment`

Template text：

```java
*
 * [请在此处描述该方法的核心功能、业务边界和异常行为]
 * $params$
 * @return $return$
 * @author Jinyang Xu
 * @date $date$ $time$
 */
```

Edit Variables：

| 变量名 | Expression | Default value |
|---|---|---|
| `date` | `date("yyyy-MM-dd")` | 无 |
| `time` | `time("HH:mm:ss")` | 无 |
| `return` | `methodReturnType()` | 无 |
| `params` | 见下方 Groovy 脚本 | 无 |

`params` Groovy 脚本：

```groovy
groovyScript("def result=''; def params='${_1}'.replaceAll('[\\[|\\]\\s]', '').split(',').toList(); for(i = 0; i < params.size(); i++) { if(params[i] != '') { result += '* @param ' + params[i] + ' ' + (i == params.size() - 1 ? '' : '\\n ') } }; return result", methodParameters())
```

使用方式：在 Java 方法定义上方输入 `/**`，再按 `Tab` 或 `Enter` 展开模板。

## 11. Maven 和 CI 自动检查

本项目通过 `maven-checkstyle-plugin` 在 Maven `verify` 阶段执行 `checkstyle.xml`。

本地完整验证：

```powershell
.\mvnw.cmd verify
```

只跑测试：

```powershell
.\mvnw.cmd test
```

GitHub Actions 使用：

```bash
./mvnw -q verify
```

因此 Pull Request 或 push 到主分支时，Checkstyle 违规会导致 CI 失败。

第一版自动化强制检查以下硬规则：

- `src/main/java` 下公开顶层类型必须有 Javadoc。
- 禁止行尾尾随注释。
- `TODO` 必须符合 `// TODO: [Owner] Description (Target date or condition)`。
- `FIXME` 必须符合 `// FIXME: Description`。
- 禁止 tab 和行尾空格。
- 禁止星号导入和未使用导入。

方法级 Javadoc 的质量要求先通过本文档、`AGENTS.md` 和代码评审执行。后续当历史代码全部收敛后，可以逐步打开更严格的 `MissingJavadocMethod` 检查。

## 12. 检查清单

每次补注释或代码评审时，按下面清单检查：

- 注释是否解释了代码无法直接表达的原因或边界？
- 注释是否会随着代码变化而容易过期？
- 注释是否重复了方法名、变量名、SQL 名称？
- 注释是否说明了 Java 后端和 Python AI 服务之间的契约？
- Mapper XML 中复杂 SQL 是否说明了业务目的？
- Service 中复杂装配是否说明了为什么不放在 XML 中？
- 新增公开类型是否有类级 Javadoc？
- 新增 TODO/FIXME 是否符合格式？
- 是否运行过 `mvnw.cmd verify`？
