# 性别感知推荐强过滤设计

日期：2026-07-01

## 1. 文档目的

本文档讨论 AI 穿搭推荐中“用户明确要男性商品，却推荐出女性半裙”的业务问题，并给出第一阶段最小闭环设计。

本次先写设计，不改运行代码。用户确认后，再进入实现计划和代码修改。

核心结论：

- 性别属于商品候选池的硬约束，不应该只交给 Python 排序降权。
- 第一阶段采用强过滤：男性需求只允许 `male` 和 `unisex` 商品进入候选池；女性需求只允许 `female` 和 `unisex` 商品进入候选池。
- 商品事实仍由 Java 商品库维护，Python 只能基于 Java 给出的候选商品排序和解释。
- 先复用现有 `product_attribute` 表承载 `适用性别`，避免第一阶段改动主表结构。

## 2. 当前问题

当前推荐链路已经有以下能力：

- Java 从商品库查询可售 SKU 候选。
- Java 把候选商品、用户画像、聊天历史组装给 Python。
- Python 根据意图、预算、颜色、尺码、风格、显高显瘦等因素给候选商品打分。
- Java 只接受 Python 从当前候选池中返回的 `spuId + skuId`，防止 Python 编造商品。

但当前商品事实缺少“适用性别”维度：

- `product_spu` 没有性别字段。
- `product_sku` 没有性别字段。
- `product_attribute` 里已有场景、风格、视觉效果、版型等推荐标签，但没有稳定的 `适用性别` 标签。
- `RecommendationCandidateQuery` 当前只有 `category/style/season/material/fit/budgetMax`，没有 `gender`。
- `AssistantContextService` 构造候选查询时没有把用户消息中的“男生/女生”或用户档案里的 `gender` 转成商品过滤条件。

所以当用户输入“男生 显高显瘦”时，系统只知道显高显瘦，不知道要排除女性商品。半裙如果带了“显高/显瘦/通勤”等标签，就可能被 Python 排到前面。

这不是前端展示问题，也不是单纯的 Python 排序问题。根因是 Java 候选池缺少性别硬过滤。

## 3. 业务规则

### 3.1 性别是强过滤

本次确认采用强过滤：

```text
用户要男性商品 -> 只允许 male + unisex
用户要女性商品 -> 只允许 female + unisex
用户没有明确性别 -> 不按性别过滤
```

不做“女性商品降权但仍可能出现”。如果用户明确说“男生”，女性半裙不应该出现在推荐商品列表中。

### 3.2 性别来源优先级

性别来源按优先级处理：

```text
本轮用户明确表达 > 请求参数 gender > 用户身体数据 gender > 用户基础资料 gender > 不过滤
```

原因：

- 本轮用户明确表达最可信。例如“给男朋友买外套”，即使登录用户本人是女性，也应该按男性商品过滤。
- 请求参数可供前端筛选器或后续接口调用使用。
- 用户身体数据和基础资料只能作为默认值，不能覆盖本轮明确需求。
- 如果无法确定性别，不要猜，保持不过滤。

### 3.3 支持的标准值

第一阶段只定义三个商品适用性别值：

```text
male
female
unisex
```

不引入更复杂的人群标签。儿童、老人、大码、孕妇、情侣款等后续可以用同样的硬约束机制扩展，但不放进第一阶段。

### 3.4 中文表达映射

Java 层可以先用轻量规则识别本轮消息：

```text
男性：男、男生、男性、男士、男款、男朋友、爸爸、男友
女性：女、女生、女性、女士、女款、女朋友、妈妈、女友
```

第一阶段不做复杂语义理解。遇到冲突表达，例如“男生女生都可以”，按不明确处理，不做性别过滤。

## 4. 方案对比

### 方案 A：只在 Python 排序时降权

做法：

- Java 候选池不变。
- Python 解析用户性别需求。
- Python 遇到不匹配性别的商品时降权或跳过。

优点：

- Java 改动少。
- Python 已有偏好解析和排序逻辑。

缺点：

- 商品事实仍然不完整，Python 需要理解“半裙是女性商品”等事实，容易越界。
- 前端通过 `/api/products/recommendation-candidates` 直接加载候选时仍会看到错误商品。
- 违反当前项目边界：商品事实和可购买候选应该由 Java 控制。

结论：不推荐。

### 方案 B：给 `product_spu` 增加正式 `target_gender` 字段

做法：

- 数据库 `product_spu` 新增 `target_gender` 字段。
- 所有商品必须设置 `male/female/unisex`。
- 推荐候选 SQL 直接按字段过滤。

优点：

- 数据模型清晰。
- SQL 查询简单。
- 更适合长期真实电商商品库。

缺点：

- 第一阶段需要迁移主表、改模型、改详情接口、补历史数据。
- 当前项目已经用 `product_attribute` 承载推荐属性，直接改主表会扩大改动面。
- 后续如果还有“年龄段/人群/适用对象”等类似约束，主表会继续膨胀。

结论：适合后续商品模型稳定后再做，不作为第一阶段最小闭环。

### 方案 C：复用 `product_attribute` 做适用性别标签

做法：

- 在 `product_attribute` 增加商品事实标签：

```text
attr_name = '适用性别'
attr_value = 'male' | 'female' | 'unisex'
```

- `RecommendationCandidateQuery` 增加 `gender`。
- `ProductMapper.findRecommendationCandidates` 在候选 SQL 中按 `适用性别` 强过滤。
- `AssistantContextService` 从本轮消息、请求参数和用户画像中解析目标性别。
- Python 保留候选内排序，不负责商品性别事实判断。

优点：

- 改动少，复用已有属性表。
- 不改主表结构。
- 能马上修复“男生推荐半裙”的问题。
- 后续年龄段、人群、适用对象等硬约束也能按同一模式扩展。

缺点：

- SQL 需要 `EXISTS` 子查询，字段不像主表字段那么直观。
- 如果未来商品量大，可能需要索引或迁移到正式字段。

结论：推荐第一阶段采用。

## 5. 推荐设计

第一阶段采用方案 C。

设计目标：

- 男性需求不出现女性商品。
- 女性需求不出现男性商品。
- 中性商品可以同时服务男性和女性需求。
- 未识别性别时不主动过滤，避免误杀。
- Java 是商品硬过滤入口，Python 不承担商品事实判断。

## 6. 数据设计

### 6.1 商品属性标签

新增一条或多条迁移脚本，为现有商品补充：

```sql
INSERT INTO product_attribute (spu_id, attr_name, attr_value)
VALUES
  (1101, '适用性别', 'male'),
  (1110, '适用性别', 'female'),
  (1001, '适用性别', 'unisex');
```

第一阶段建议规则：

```text
明确男款：male
明确女款：female
T 恤、卫衣、部分外套、部分裤装：根据商品名和业务定位标 male 或 unisex
半裙：female
```

不要为了让测试通过乱标 `unisex`。如果业务上明显是女款半裙，就必须标 `female`。

### 6.2 索引建议

第一阶段商品量小，可以不新增索引。

如果后续候选查询变慢，再新增：

```sql
CREATE INDEX idx_product_attribute_spu_name_value
ON product_attribute (spu_id, attr_name, attr_value);
```

当前先不加，避免为小数据集提前优化。

## 7. Java 后端设计

### 7.1 DTO 增加 gender

`RecommendationCandidateQuery` 增加：

```java
private String gender;
```

`AssistantChatRequest` 可选增加：

```java
String gender
```

说明：

- `RecommendationCandidateQuery.gender` 是 Java 内部候选池过滤条件。
- `AssistantChatRequest.gender` 是给前端筛选器和后续显式调用预留的入口。
- 即使前端第一阶段不加筛选器，后端也能通过消息和用户画像解析。

### 7.2 性别解析

在 `AssistantContextService` 中新增一个小方法：

```text
resolveTargetGender(request, bodyData, profile)
```

解析顺序：

```text
1. 从 request.message 识别本轮性别
2. 如果 request.gender 有值，使用 request.gender
3. 如果 bodyData.gender 有值，使用 bodyData.gender
4. 如果 profile.gender 有值，使用 profile.gender
5. 返回 null
```

标准化输出：

```text
male
female
null
```

如果消息同时出现男性和女性信号：

```text
返回 null，不过滤
```

这是最省事也最安全的第一版处理方式。

### 7.3 候选 SQL 强过滤

`ProductMapper.findRecommendationCandidates` 增加：

```sql
AND EXISTS (
    SELECT 1
    FROM product_attribute gender_attr
    WHERE gender_attr.spu_id = p.id
      AND gender_attr.attr_name = '适用性别'
      AND gender_attr.attr_value IN (#{genderValue}, 'unisex')
)
```

只在 `genderValue` 非空时启用。

如果 `genderValue = male`：

```text
允许 male/unisex
排除 female
```

如果 `genderValue = female`：

```text
允许 female/unisex
排除 male
```

### 7.4 缓存 key

`ProductCatalogService.canonicalRecommendationQuery` 必须加入 `gender`：

```text
gender=male
```

否则会出现缓存污染：

- 第一次男性请求缓存了男性候选。
- 第二次女性请求命中同一个缓存 key。
- 女性请求错误拿到男性候选。

这是必须改的点。

## 8. Python AI 设计

第一阶段 Python 只做兜底，不作为主过滤入口。

可选改动：

- `preference_parser` 可以识别 `male/female` 作为偏好字段。
- `recommendation_service` 如果候选里带 `适用性别:female` 且用户上下文是 `male`，可以跳过。

但这不是第一优先级。只要 Java 候选池强过滤正确，Python 正常只会看到合法候选。

Python 不应该：

- 自己推断某个商品是不是男款或女款。
- 因为商品名像女款就自行排除 Java 候选。
- 编造商品性别事实。

## 9. 前端设计

第一阶段可以不加性别筛选 UI，只靠自然语言和用户资料。

后续可以在 `ChatPanel` 的筛选区增加一个性别选择：

```text
不限 / 男性 / 女性
```

请求字段：

```ts
gender?: "male" | "female"
```

前端展示线索可以增加：

```text
适用性别：男性
```

但第一阶段不强制做 UI，先保证用户输入“男生”时后端候选池正确过滤。

## 10. 测试设计

### 10.1 Mapper 测试

新增或扩展 `ProductCatalogMapperTests`：

```text
male 查询不包含 female 商品
female 查询不包含 male 商品
male 查询允许 unisex 商品
未传 gender 时不按性别过滤
```

重点断言：

```text
男生显高显瘦 -> 不出现 SKIRT_* 商品
女生通勤 -> 可以出现 SKIRT_* 商品
```

### 10.2 Service 测试

扩展 `AssistantContextServiceTests`：

```text
消息包含“男生” -> query.gender = male
消息包含“女生” -> query.gender = female
消息没有性别但 bodyData.gender = male -> query.gender = male
消息包含“给女朋友买”且用户档案是 male -> query.gender = female
消息同时包含男生女生 -> query.gender = null
```

### 10.3 Python 回归测试

如果 Python 增加兜底过滤，则补一条最小测试：

```text
user_context.gender = male
candidate.attribute_tags 包含 适用性别:female
build_product_refs 不返回该 candidate
```

如果第一阶段 Python 不改，可以不加 Python 测试。

### 10.4 前端测试

第一阶段如果不改 UI，不需要新增前端测试。

如果后续增加性别筛选器，则补：

```text
选择“男性”后，请求包含 gender=male
```

## 11. 验收标准

用户确认实现后，至少满足：

- 输入 `男生 显高显瘦` 时，推荐列表不出现半裙等 `female` 商品。
- 输入 `女生 显高显瘦` 时，允许出现半裙等 `female` 商品。
- 输入 `男士通勤外套` 时，只返回 `male/unisex` 候选。
- 输入 `给女朋友买一件通勤半裙` 时，即使登录用户是男性，也按 `female/unisex` 过滤。
- 未表达性别时，系统不因为用户档案缺失而报错。
- 推荐候选缓存不会在不同 gender 之间串结果。

## 12. 第一阶段开发范围

建议第一阶段只改这些点：

- 新增数据库迁移，补 `适用性别` 属性数据。
- `RecommendationCandidateQuery` 增加 `gender`。
- `AssistantChatRequest` 可选增加 `gender`。
- `AssistantContextService` 解析目标性别。
- `ProductMapper.findRecommendationCandidates` 加性别强过滤。
- `ProductCatalogService` 把 `gender` 纳入缓存 key。
- 后端补最小测试。

不做：

- 不新增复杂人群模型。
- 不改 `product_spu` 主表。
- 不新增前端筛选器，除非用户确认要做。
- 不让 Python 接管商品性别事实。
- 不重构整个推荐算法。

## 13. 后续扩展方向

这次的设计可以复用到其他“硬约束”：

```text
适用年龄段：adult / teen / senior
适用人群：student / commute / outdoor
适用场景：interview / daily / sport
适用季节：spring / summer / autumn / winter
禁用属性：not_for_formal / not_for_winter
```

但后续新增硬约束时要先判断：

- 是不是商品事实？
- 是不是用户明确要求后必须强过滤？
- 是不是应该由 Java 候选池控制？
- 是不是会影响缓存 key？

如果答案是 yes，就按本次 `gender` 模式扩展。

## 14. 用户确认点

开始实现前，需要确认：

- 是否确认第一阶段采用 `product_attribute` 的 `适用性别` 标签，而不是直接改 `product_spu` 主表？
- 是否确认性别使用强过滤：`male -> male/unisex`，`female -> female/unisex`？
- 是否确认第一阶段不新增前端性别筛选 UI，只先支持自然语言和用户画像？
- 是否确认遇到“男生女生都可以”这类冲突表达时，不按性别过滤？
- 是否确认 Python 只做候选内排序，不负责商品性别事实判断？

用户确认后，再进入实现计划。
