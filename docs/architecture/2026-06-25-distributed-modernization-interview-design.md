# 智能穿搭项目分布式与现代化能力演进设计

## 1. 背景

当前项目已经形成了较完整的智能穿搭电商闭环：

- Java Spring Boot 后端负责用户、商品、库存、购物车、订单、支付、收藏、会话和 AI 接口编排。
- React 前端负责商品浏览、购物车、订单和 AI 导购交互。
- Python AI 服务负责 LangGraph Agent、RAG、推荐回答和工具调用。
- MySQL 负责交易事实数据，Flyway 管理数据库迁移。

后续如果为了面试项目亮点引入 Redis、MQ、微服务等现代化能力，核心目标不是“堆技术栈”，而是让技术服务于项目主线：

```text
用户画像 -> AI 导购推荐 -> 商品卡片 -> 加购/收藏 -> 下单/支付 -> 行为反馈 -> 推荐优化
```

因此本项目的演进策略是：先在模块化单体中补齐缓存、异步事件、熔断降级、链路追踪等工程能力，再根据边界逐步拆分服务。

## 2. 总体原则

### 2.1 不急于一开始拆微服务

当前阶段推荐继续保持 Java 后端为模块化单体：

```text
auth
user
product
inventory
cart
order
payment
favorite
conversation
assistant
behavior
common
```

原因：

- 当前项目重点是 AI 导购与电商交易闭环，而不是微服务基础设施本身。
- 过早拆分会引入服务注册、配置中心、网关、分布式事务、部署和联调成本。
- 模块化单体更适合毕业设计、实训项目和面试展示，能说明边界清晰，也能保持可运行。

推荐面试表达：

> 项目前期采用模块化单体保证开发效率，把领域边界先划清楚；当商品、订单、AI 推荐等模块复杂度上升后，再按业务边界演进为微服务。

### 2.2 所有现代化能力必须绑定业务场景

不要只说“用了 Redis、MQ、微服务”。应该说明：

- Redis 解决哪些高频读、热点数据和限流问题。
- MQ 解决哪些长耗时、跨模块副作用和事件解耦问题。
- 熔断降级解决 AI 服务不可用时的主流程稳定性问题。
- 微服务拆分服务于商品、订单、推荐、AI 导购这些天然边界。

## 3. Redis 设计

Redis 是本项目最适合优先引入的现代化能力，收益高、复杂度可控。

### 3.1 适用场景

推荐优先缓存：

```text
商品详情缓存
推荐候选商品缓存
用户画像缓存
AI 会话临时上下文
验证码
AI 聊天接口限流
热门商品 / 热门风格标签
```

### 3.2 商品详情缓存

商品详情、SKU 信息、图片、风格标签、材质、尺码等数据读多写少，适合缓存。

建议 key：

```text
product:detail:{spuId}
product:sku:{skuId}
product:recommendation-candidates:{hash}
```

价值：

- 商品浏览和 AI 推荐候选查询不用每次都打 MySQL。
- Python AI 服务间接依赖 Java internal product API，Java 可通过 Redis 加速 internal API。
- 面试中可以说明缓存穿透、缓存击穿、缓存雪崩的处理思路。

### 3.3 用户画像缓存

AI 推荐需要用户身高、体重、风格偏好、预算、颜色偏好等画像数据。这类数据修改频率较低，但 AI 聊天会频繁读取。

建议 key：

```text
user:profile:{userId}
user:preference:{userId}
```

价值：

- 减少每次 AI 对话前组装上下文的数据库查询。
- 支持推荐服务快速读取用户偏好。
- 用户修改画像后主动删除或刷新缓存即可。

### 3.4 接口限流

AI 聊天接口成本高、耗时长，适合用 Redis 做限流。

建议 key：

```text
rate-limit:assistant:{userId}:{minute}
rate-limit:assistant:{ip}:{minute}
```

价值：

- 防止单用户短时间大量请求打爆 Python AI 服务。
- 面试中可以体现对 AI 成本、接口保护和系统稳定性的考虑。

### 3.5 第一版 Redis 落地范围

第一版不要全项目到处加缓存，优先做四个小核心点：

```text
1. 商品详情缓存
2. 用户画像缓存
3. 推荐候选缓存
4. AI 聊天接口限流
```

原因：

- 这四个点都直接服务 AI 导购主链路。
- 改动范围可控，不会影响订单、支付、库存等交易一致性。
- 面试时能讲清楚 Redis 不是硬加的，而是为高频读、推荐上下文和 AI 服务保护服务。

暂不建议第一版缓存：

```text
订单最终状态
支付最终状态
库存扣减结果
交易流水
```

这些数据必须以 MySQL 为事实源，Redis 不能替代数据库。

### 3.6 Cache Aside 读写模式

商品详情、用户画像、推荐候选都建议采用 Cache Aside 模式。

读取流程：

```text
业务请求
-> 先查 Redis
-> Redis 命中，直接返回
-> Redis 未命中，查 MySQL
-> MySQL 查到数据，写入 Redis 并设置 TTL
-> 返回结果
```

商品详情伪流程：

```text
getProductDetail(spuId):
  key = product:detail:{spuId}

  1. 先查 Redis
  2. 命中：反序列化后返回
  3. 未命中：查 MySQL
  4. MySQL 查到：写 Redis，设置 TTL
  5. 返回商品详情
```

面试表达：

> 我采用 Cache Aside 模式，商品详情先查 Redis，未命中再查 MySQL，并把结果回写缓存。这样 MySQL 仍然是数据事实源，Redis 只作为加速层。

### 3.7 Redis Key 设计

Key 命名原则：

```text
业务域:对象名:唯一标识
```

建议 key：

```text
product:detail:{spuId}
product:sku:{skuId}
product:recommendation-candidates:{queryHash}

user:profile:{userId}
user:preference:{userId}

assistant:rate-limit:user:{userId}:{minute}
assistant:rate-limit:ip:{ip}:{minute}

hot:product:daily
hot:style-tag:daily
```

示例：

```text
product:detail:1001
user:profile:12
user:preference:12
assistant:rate-limit:user:12:202606251503
```

不建议使用模糊 key：

```text
data:1
cache:user
product
```

这些 key 后期很难排查，也难以按业务域清理。

### 3.8 TTL 策略

不同数据的过期时间应该不同：

```text
商品详情：30 分钟 - 2 小时
SKU 信息：10 - 30 分钟
推荐候选：5 - 15 分钟
用户画像：10 - 30 分钟
AI 限流 key：1 分钟
热门商品：1 小时 - 1 天
```

设计原因：

- 商品详情读多写少，TTL 可以稍长。
- SKU 信息和库存、价格关系更近，TTL 应更短。
- 推荐候选由多个筛选条件组合而来，适合短 TTL，避免精确删除复杂度过高。
- 用户画像修改频率低，但 AI 对话读取频繁，适合中短 TTL。
- 限流 key 只用于窗口统计，通常 60 秒即可。

TTL 建议增加随机抖动：

```text
实际 TTL = 基础 TTL + 随机 0-5 分钟
```

这样可以降低大量 key 同时过期导致的缓存雪崩风险。

### 3.9 缓存失效策略

商品被修改时：

```text
更新 MySQL 商品数据
-> 删除 product:detail:{spuId}
-> 删除相关 product:sku:{skuId}
-> 推荐候选缓存等待短 TTL 自动过期
```

用户画像被修改时：

```text
更新 MySQL 用户画像
-> 删除 user:profile:{userId}
-> 删除 user:preference:{userId}
```

推荐候选缓存不建议第一版做精确删除。因为候选 key 通常由品类、风格、季节、预算等组合生成，精确反查所有相关 key 成本较高。第一版使用短 TTL 更稳。

推荐更新顺序：

```text
先更新 MySQL
再删除 Redis 缓存
```

不推荐：

```text
先删除缓存
再更新 MySQL
```

原因是如果删除缓存后数据库更新失败，缓存和数据库可能出现更难排查的不一致。

### 3.10 四个核心缓存点

#### 3.10.1 商品详情缓存

适合缓存：

```text
商品名
商品图片
价格展示字段
颜色
尺码
材质
风格标签
季节
版型
商品描述
```

不适合强缓存：

```text
实时库存
订单状态
支付状态
```

项目价值：

- 前端商品详情页、商品卡片都依赖商品详情。
- Python AI 服务通过 Java internal product API 获取商品事实，Java 可用 Redis 加速 internal API。
- AI 推荐候选生成时会频繁读取商品结构化字段。

#### 3.10.2 用户画像缓存

适合缓存：

```text
身高
体重
性别
尺码偏好
风格偏好
颜色偏好
预算区间
不喜欢的颜色
```

项目价值：

- 用户画像是 AI 推荐上下文。
- 每次 AI 聊天前都查 MySQL 会增加前置耗时。
- 用户画像修改后删除缓存，下一次请求重新加载即可。

#### 3.10.3 推荐候选缓存

当用户提出需求：

```text
适合通勤的秋季外套，预算 400 以内
```

系统可能转换为结构化查询：

```text
category=外套
style=commute
season=autumn
budgetMax=400
```

可以生成 queryHash：

```text
product:recommendation-candidates:{queryHash}
```

缓存内容：

```text
spuId
skuId
商品名
价格
风格标签
季节
材质
版型
推荐所需基础字段
```

推荐候选缓存 TTL 不宜过长，第一版建议 5-15 分钟。

#### 3.10.4 AI 接口限流

AI 聊天接口会调用 Python、LangGraph、LLM、RAG，成本和耗时都高于普通商品接口。

限流规则：

```text
用户每分钟最多请求 N 次 AI 聊天
IP 每分钟最多请求 N 次 AI 聊天
超过阈值直接返回限流提示
```

Redis 操作思路：

```text
INCR assistant:rate-limit:user:{userId}:{minute}
第一次创建 key 时设置 EXPIRE 60 秒
计数超过阈值则拒绝请求
```

面试表达：

> AI 接口和普通商品接口不同，它有模型调用成本和长耗时风险，所以我用 Redis 做用户级和 IP 级限流，保护 Python AI 服务。

### 3.11 Java 代码结构建议

第一版引入 Redis 时，建议避免在各个 service 里直接散落 `RedisTemplate` 调用。

建议结构：

```text
common/cache
  CacheKeyConstants
  CacheTtlProperties
  RedisCacheService

product/service
  ProductCacheService

user/service
  UserProfileCacheService

assistant/service
  AssistantRateLimitService
```

基础依赖：

```text
spring-boot-starter-data-redis
RedisConnectionFactory
StringRedisTemplate 或 RedisTemplate
JSON 序列化配置
CacheManager
```

本地依赖：

```text
docker-compose.yml 增加 redis:7
application.properties 配置 spring.data.redis.host 和 spring.data.redis.port
```

### 3.12 Redis 常见坑与处理

#### 缓存穿透

问题：

```text
用户查询不存在的商品 id，每次都打到 MySQL。
```

处理：

```text
缓存空值，TTL 设置短一点，例如 1-5 分钟。
```

#### 缓存雪崩

问题：

```text
大量 key 在同一时间过期，瞬间请求都打到 MySQL。
```

处理：

```text
TTL 增加随机抖动。
```

#### 缓存击穿

问题：

```text
某个热门商品缓存刚好过期，大量请求同时打到 MySQL。
```

第一版处理：

```text
先接受短时间回源，保留监控。
```

后续优化：

```text
热点 key 使用互斥锁或逻辑过期。
```

#### Redis 被误用为数据库

问题：

```text
把订单、支付、库存最终状态长期存在 Redis。
```

处理：

```text
交易事实以 MySQL 为准，Redis 只做缓存、限流、临时状态和热点统计。
```

### 3.13 Redis 第一版开发顺序

建议开发顺序：

```text
1. docker-compose 增加 Redis
2. Java 后端增加 Redis 依赖和配置
3. 封装 CacheKeyConstants、CacheTtlProperties、RedisCacheService
4. 商品详情接入缓存：product:detail:{spuId}
5. 用户画像接入缓存：user:profile:{userId}
6. 推荐候选接入缓存：product:recommendation-candidates:{queryHash}
7. AI 聊天接口接入限流：assistant:rate-limit:user:{userId}:{minute}
```

第一版验收标准：

```text
商品详情第二次查询命中 Redis
用户画像第二次读取命中 Redis
推荐候选相同条件短时间内命中 Redis
AI 聊天接口超过阈值返回限流提示
更新商品或用户画像后，对应缓存被删除
```

### 3.14 Redis 面试表达补充

可以这样说：

> 我没有把 Redis 当成简单的技术点硬加，而是围绕 AI 导购链路做缓存设计。商品详情、用户画像、推荐候选都是 AI 推荐的高频读取数据，所以我用 Redis 做 Cache Aside 缓存；用户修改画像或商品更新后，先更新 MySQL，再删除缓存，保证 MySQL 是事实源。同时，AI 聊天接口成本较高，我用 Redis 做用户级和 IP 级限流，避免短时间大量请求打爆 Python Agent 服务。

## 4. MQ 设计

MQ 适合处理用户不需要同步等待的任务。本项目推荐在订单、支付、用户行为和 AI 任务中引入事件驱动。

### 4.1 推荐事件

```text
order.created
payment.succeeded
order.cancelled
cart.item.added
product.viewed
product.favorited
recommendation.clicked
recommendation.converted
ai.task.requested
ai.task.completed
```

### 4.2 支付成功事件

同步主流程：

```text
用户支付 -> 更新支付状态 -> 返回支付成功
```

异步副作用：

```text
payment.succeeded
-> 扣减或确认库存
-> 记录购买行为
-> 更新推荐转化数据
-> 触发用户画像刷新
```

价值：

- 支付接口不承担过多职责。
- 行为分析、推荐反馈、画像更新失败时，不影响支付成功结果。
- 面试中可以讲清楚最终一致性和事件驱动。

### 4.3 用户行为事件

推荐系统最重要的是行为闭环。用户浏览、点击、收藏、加购、下单都应该沉淀。

建议新增行为模型：

```text
user_product_behavior:
  id
  user_id
  spu_id
  sku_id
  behavior_type
  source
  session_id
  recommendation_id
  created_at
```

`behavior_type` 示例：

```text
view
click
favorite
add_to_cart
order
cancel
refund
```

价值：

- 证明推荐不是一次性问答，而是可持续优化的闭环。
- 后续可用于热门商品、个性化召回、推荐转化分析。
- 面试时能把 AI Agent 和传统推荐系统联系起来。

### 4.4 AI 长任务

适合异步化的 AI 任务：

- RAG 知识库重建。
- 商品文案批量生成。
- 商品标签批量补全。
- 推荐效果评估报告。
- 用户画像定时刷新。

建议流程：

```text
Java 创建 ai_task
-> 发送 ai.task.requested
-> Python 消费任务
-> Python 执行 LangGraph / RAG / LLM 任务
-> 发送 ai.task.completed
-> Java 保存结果
-> 前端轮询或 SSE 获取状态
```

## 5. AI 服务熔断与降级

Python AI 服务不可用时，商城主流程不能被拖垮。

建议加入：

- 超时控制。
- 重试策略。
- 熔断。
- 降级响应。
- requestId / traceId 链路日志。

降级策略：

```text
AI 聊天不可用 -> 返回明确提示
商品浏览可用
购物车可用
下单支付可用
基于规则的热门商品推荐可用
```

面试表达：

> AI 是增强体验，不是交易主链路的强依赖。即使 Python Agent 不可用，用户仍然可以浏览商品、加购、下单。

## 6. 微服务演进路线

### 6.1 第一阶段：模块化单体

保持当前 Java 后端单应用，但强化模块边界。

目标：

- 每个模块有独立 controller、service、mapper、model、dto。
- 跨模块调用尽量通过 service 接口，不直接操作别的模块 mapper。
- 通过事件表或 MQ 设计为后续拆分留接口。

### 6.2 第二阶段：外部 AI 服务独立化

当前 Python AI 服务已经天然独立。Java 的 assistant 模块本质上是 AI 网关。

推荐边界：

```text
前端 -> Java assistant API -> Python AI Service -> Java internal API
```

Java 仍然是商品、库存、订单、用户画像的数据事实源。

### 6.3 第三阶段：拆分高价值服务

如果需要演示微服务架构，建议按以下顺序拆：

```text
product-service
order-service
inventory-service
recommendation-service
assistant-service
```

不建议优先拆：

- favorite-service：业务轻，拆分收益低。
- cart-service：前期复杂度不高。
- user-profile：除非画像体系复杂起来。

### 6.4 可能的微服务架构

```text
Frontend
  -> API Gateway
      -> auth-service
      -> user-service
      -> product-service
      -> inventory-service
      -> cart-service
      -> order-service
      -> payment-service
      -> recommendation-service
      -> assistant-service
            -> Python AI Service
```

基础设施：

```text
MySQL
Redis
MQ
Gateway
Config Center
Service Registry
Tracing
```

注意：这些基础设施适合放到后续阶段，不应在项目初期一次性全部引入。

## 7. 推荐实施优先级

### 7.1 面试亮点优先版本

```text
1. Redis：商品详情、用户画像、推荐候选、AI 限流
2. 用户行为表：view / click / favorite / add_to_cart / order
3. MQ：支付成功、推荐转化、AI 长任务
4. 熔断降级：Python AI 服务不可用时不影响商城主流程
5. 链路追踪：requestId / traceId 串联 Java、Python 和前端请求
6. Docker Compose：MySQL + Redis + MQ + Java + Python 一键启动
```

### 7.2 不建议优先投入

```text
1. 一开始拆十几个微服务
2. 为了微服务而引入复杂分布式事务
3. 过早引入 Nacos、Gateway、Sentinel 全家桶
4. 没有业务场景地使用 Kafka
5. 把 Redis 当数据库长期存交易事实
```

## 8. 面试表达模板

### 8.1 为什么引入 Redis

> 项目里商品详情、用户画像和推荐候选都是高频读取数据，尤其 AI 推荐每次都要组装上下文。如果完全查 MySQL，会增加响应时间和数据库压力。所以我引入 Redis 缓存商品详情、用户画像和推荐候选，并对 AI 接口做限流，既提升性能，也保护 Python AI 服务。

### 8.2 为什么引入 MQ

> 下单和支付后会产生很多副作用，比如记录用户行为、更新推荐转化、刷新用户画像，这些不应该全部阻塞在主接口里。所以我通过 MQ 把核心交易链路和推荐反馈链路解耦，主流程快速返回，异步事件负责后续分析和推荐优化。

### 8.3 为什么不一开始拆微服务

> 我没有一开始就拆微服务，因为当前项目更需要保证 AI 导购到下单的闭环跑通。过早拆分会增加部署、联调和一致性成本。所以我先采用模块化单体，把 auth、product、order、assistant 等边界划清楚，再通过缓存、MQ、接口契约为后续微服务拆分做准备。

### 8.4 AI 服务不可用怎么办

> AI 服务是增强体验，不是交易主链路强依赖。如果 Python Agent 超时或不可用，Java 会通过超时、熔断和降级返回提示，同时保留商品浏览、购物车、下单和支付能力。这样系统不会因为 AI 服务异常导致整个商城不可用。

## 9. 结论

本项目引入现代化能力的正确顺序不是直接上微服务，而是：

```text
模块化单体
-> Redis 缓存与限流
-> MQ 异步事件
-> 行为数据与推荐闭环
-> AI 服务熔断降级
-> 按业务边界拆分微服务
```

这样既符合真实系统演进逻辑，也更适合作为面试项目亮点表达。
