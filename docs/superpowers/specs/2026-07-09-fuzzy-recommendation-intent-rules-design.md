# 模糊推荐话术解析开发文档

日期：2026-07-09

## 1. 目标

把用户的模糊推荐需求稳定转成系统能落地的少量标准意图，而不是给每一种问法都写一条专用规则。

典型输入：

```text
秋冬保暖的，平价百搭一点
有没有不容易冷的外套
冬天穿，想要暖和点，但别太贵，最好日常也能穿
大学生日常上课，别太贵，还要遮肉显腿长
```

目标输出方向：

```text
Java DemandIntent：负责标准字段、候选池硬过滤、字段安全
Python rerank：负责候选池内软偏好排序和推荐理由
```

## 2. 不解决什么

本次不做：

- 不新建 LLM Router。
- 不新增数据库字段。
- 不让大模型直接决定商品 ID。
- 不把每句新话术都变成一条业务规则。
- 不改 Java-Python 字段契约；`demand_intent` 字段已经存在。

## 3. 规则沉淀原则

只有满足下面条件的词，才进入 Java 规则词典：

| 条件 | 例子 | 处理 |
| --- | --- | --- |
| 高频稳定 | 保暖、平价、百搭、学生党、通勤 | 加规则 |
| 能映射到现有字段 | 外套 -> category，冬天 -> season | 加规则 |
| 会影响候选池 | 性别、分类、预算数字、季节 | Java 解析 |
| 主观审美 | 松弛感、高级感、甜酷、氛围感 | 暂不硬筛，交给 Python 软排序 |
| 数据库没有字段 | “看起来贵一点” | 不造字段，不硬过滤 |

核心原则：

```text
用户表达很多，系统 intent 很少。
新增规则不是新增句子，而是把同义表达归一到已有 intent。
```

## 4. 推荐方案

采用：

```text
Java 规则优先 + Python rerank 消费 DemandIntent
```

流程：

```text
用户输入
  ↓
Java DemandIntentResolver 解析高频稳定字段
  ↓
Java 用 gender/category/budget/season 收敛候选池
  ↓
Python 接收 demand_intent
  ↓
Python 把 scene/style/attributes 转成 rerank 加分和推荐理由
  ↓
只从 Java candidates 返回 product_refs
```

## 5. Java 开发范围

文件：

```text
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentResolver.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java
backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java
```

### 5.1 补充同义词组

只补高频组：

```text
保暖组：
秋冬、冬季、冬天、保暖、怕冷、暖和、不容易冷、厚款、厚实、加绒、抗冻

平价组：
平价、便宜、不贵、别太贵、预算有限、学生预算、性价比

百搭组：
百搭、好搭、基础款、日常也能穿、不挑场合、一衣多穿

学生组：
学生党、学生、大学生、校园、上课、上学

显瘦/显高组：
显瘦、遮肉、不显胖、梨形、腿粗、胯宽
显高、小个子、显腿长、不压个子
```

### 5.2 字段落地规则

```text
冬天/保暖类 -> season=winter，attributes += 保暖/厚款
外套/夹克/风衣/羽绒服 -> category=外套
平价类 -> attributes += 平价，不做预算硬过滤
百搭类 -> style += minimal/casual
学生类 -> scene += campus/daily，style += casual
显瘦/显高类 -> attributes += 显瘦/显高
数字预算 -> budgetMax，作为硬过滤
```

原因：没有数字的“别太贵”不能直接变成 `budgetMax`，否则会误杀候选；它只影响 Python 排序。

## 6. Python 开发范围

文件：

```text
AI-Clothing-Shopping-Assistant-System/clothing_assistant/api/app.py
AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/langgraph_executor.py
AI-Clothing-Shopping-Assistant-System/clothing_assistant/agent/state.py
AI-Clothing-Shopping-Assistant-System/clothing_assistant/application/recommendation_service.py
AI-Clothing-Shopping-Assistant-System/tests/test_recommendation_service.py
```

### 6.1 补上 demand_intent 传递

当前 Python API schema 已有 `demand_intent`，但运行链路主要还靠 `user_query` 解析。开发时要把它传入 LangGraph state 和 rerank。

### 6.2 最小适配方式

不重写 Python parser，只加一个小适配：

```text
DemandIntent.scene -> preferences.scene
DemandIntent.style -> preferences.style_tags
DemandIntent.attributes 包含 保暖 -> style_tags += warm，season += winter
DemandIntent.attributes 包含 平价 -> price_preference = budget
DemandIntent.attributes 包含 显瘦 -> visual_goals += slimmer
DemandIntent.attributes 包含 显高 -> visual_goals += taller
DemandIntent.budgetMax -> budget_max
```

然后和现有 `parse_preferences(user_query)` 合并。Java intent 优先级更高；Python 仍可补充长尾软偏好。

## 7. 验收用例

### 7.1 Java intent

```text
有没有不容易冷的外套
=> category=外套, season=winter, attributes 包含 保暖

秋冬保暖的，平价百搭一点
=> season=winter, attributes 包含 保暖/平价, style 包含 minimal/casual

大学生日常上课，别太贵，还要遮肉显腿长
=> scene 包含 campus/daily, attributes 包含 平价/显瘦/显高
```

### 7.2 Python rerank

```text
冬天保暖需求
=> 羊毛、羽绒、加绒、厚款、针织候选加分
=> 短袖、薄款、Polo、夏季候选不得排前

平价百搭需求
=> 低价、basic/minimal/casual、基础色候选加分

显瘦显高需求
=> 高腰、直筒、垂顺、深色候选加分
```

### 7.3 安全边界

必须保持：

```text
Python product_refs 只能来自 Java candidates
Python 不编造价格、库存、SKU
Java 交易前仍重新校验价格和库存
```

## 8. 测试命令

Java：

```bash
cd /Users/seekinward/Documents/推荐项目/IntelligentOutfitRecommendationSystem/backend
sh ./mvnw -q -Dtest=AssistantContextServiceTests test
```

Python：

```bash
cd /Users/seekinward/Documents/推荐项目/AI-Clothing-Shopping-Assistant-System
python -m unittest tests.test_recommendation_service -v
```

如果改动触碰 Java-Python 字段名，再补跑共享契约测试；本设计不计划改字段名。

## 9. 开发顺序

1. 先加 Java/Python 测试用例。
2. 补 Java 同义词组和字段归一。
3. 补 Python `demand_intent` 传递。
4. 补 Python intent-to-preferences 适配。
5. 跑最小测试。

## 10. 用户确认点

请确认是否按这个范围开发：

```text
只做高频规则 + demand_intent 贯通 + rerank 使用 intent。
暂不做 LLM Router，暂不新增数据库字段，暂不做全量话术平台。
```

## 11. 自检

- 没有新增字段契约。
- 没有让 Python 越过 Java candidates。
- 没有把“别太贵”误当成数字预算硬过滤。
- 没有要求每个新说法都写新规则。
- 范围可以用现有 Java/Python 单测验证。
