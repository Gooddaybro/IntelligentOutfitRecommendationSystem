# Redis Cache and Rate Limit MVP Implementation Plan

**Goal:** 在 Java 后端引入第一版 Redis 能力，围绕智能穿搭主链路完成商品详情缓存、用户画像缓存、推荐候选缓存和 AI 聊天接口限流。第一阶段重点是把缓存链路跑通、讲清楚、可验证，而不是把 Redis 到处乱加。

**Architecture:** 当前项目继续保持模块化单体。Redis 作为 MySQL 旁边的加速层和限流层，不作为交易事实源。商品、用户、订单、支付、库存最终状态仍以 MySQL 为准。

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data Redis, MyBatis XML, MySQL 8.0, Redis 7.2, Docker Compose, JUnit 5.

---

## 0. 当前项目现状

- 已完成商品目录、SKU、库存、推荐候选查询。
- 已完成用户注册登录、用户画像、身体数据、穿衣偏好。
- 已完成 Java 调 Python AI 服务的同步和 SSE 流式链路。
- 已完成购物车、订单、模拟支付等电商闭环。
- 当前还没有接入 Redis：
  - `docker-compose.yml` 只有 MySQL。
  - `backend/pom.xml` 还没有 Redis starter。
  - `application.properties` 还没有 Redis 连接配置。
  - 业务 Service 目前直接查 MySQL。

---

## 1. 本阶段实现范围

### 必须实现

- Docker Compose 增加 Redis 容器。
- Java 后端增加 Redis 依赖和连接配置。
- 新增统一缓存包：`common/cache`。
- 新增 Redis key 集中管理：`CacheKeyConstants`。
- 新增 TTL 配置：`CacheTtlProperties`。
- 新增 Redis 操作封装：`RedisCacheService`。
- 商品详情接入 Cache Aside 缓存：`product:detail:{spuId}`。
- 用户画像接入 Cache Aside 缓存：`user:profile:{userId}`。
- 推荐候选接入短 TTL 缓存：`product:recommendation-candidates:{queryHash}`。
- AI 聊天接口接入 Redis 限流：`assistant:rate-limit:user:{userId}:{minute}`。
- 补充单元测试或集成测试，证明命中缓存、缓存失效和限流行为。

### 暂不实现

- 不缓存订单最终状态。
- 不缓存支付最终状态。
- 不用 Redis 扣库存。
- 不用 Redis 保存唯一真实库存。
- 不做 Redis Cluster。
- 不做 Redisson 分布式锁。
- 不做复杂缓存预热系统。
- 不做推荐候选缓存的精确批量删除，第一版依赖短 TTL 自动过期。

---

## 2. 第一版开发顺序

建议严格按这个顺序做，不要跳步：

```text
1. docker-compose 增加 Redis
2. Java 后端增加 Redis 依赖
3. application.properties 增加 Redis 配置和 TTL 配置
4. 新建 common/cache 包
5. 写 CacheKeyConstants
6. 写 CacheTtlProperties
7. 写 RedisCacheService
8. 商品详情 getProductDetail 接入缓存
9. 用户画像 getProfile 接入缓存
10. 推荐候选 findRecommendationCandidates 接入缓存
11. AI chat / streamChat 接入限流
12. 补测试和手动验证步骤
```

第一天只建议做到第 1-8 步。商品详情缓存跑通后，再继续做用户画像、推荐候选和限流。

---

## 3. 文件改动清单

### 基础设施

- Modify: `docker-compose.yml`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/test/resources/application-test.properties`

### 缓存公共能力

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/CacheKeyConstants.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/CacheTtlProperties.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/RedisCacheService.java`

### 商品缓存

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Create or Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

### 用户画像缓存

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/user/service/UserProfileService.java`
- Create or Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/user/UserProfileServiceTests.java`

### 推荐候选缓存

- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Create or Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

### AI 限流

- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantRateLimitService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Create or Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

---

## 4. Redis key 设计

统一原则：

```text
业务域:对象名:唯一标识
```

第一版 key：

```text
product:detail:{spuId}
user:profile:{userId}
product:recommendation-candidates:{queryHash}
assistant:rate-limit:user:{userId}:{minute}
assistant:rate-limit:ip:{ip}:{minute}
```

不要写这种 key：

```text
data:1
cache:user
product
redis:key
```

原因：这些 key 看不出业务含义，后续排查 Redis 数据时会很痛苦。

---

## 5. TTL 策略

第一版建议：

```text
商品详情：60 分钟
用户画像：20 分钟
推荐候选：10 分钟
AI 限流 key：60 秒
```

缓存 TTL 需要加随机抖动：

```text
实际 TTL = 基础 TTL + 随机 0-5 分钟
```

原因：如果大量 key 在同一时间过期，会导致请求同时回源 MySQL，形成缓存雪崩风险。

---

## 6. Cache Aside 读写模式

商品详情、用户画像、推荐候选都使用 Cache Aside：

```text
请求进入 Service
-> 生成 Redis key
-> 查 Redis
-> 命中：直接返回
-> 未命中：查 MySQL
-> MySQL 查到：写入 Redis，设置 TTL
-> 返回结果
```

缓存失效顺序：

```text
先更新 MySQL
再删除 Redis 缓存
```

第一版不要为了“更实时”先删除缓存再更新 MySQL。数据库更新失败时，这种顺序更容易造成不一致。

---

## 7. 商品详情缓存落地说明

目标方法：

```text
ProductCatalogService#getProductDetail(Long spuId)
```

缓存 key：

```text
product:detail:{spuId}
```

适合缓存的数据：

- 商品名
- 商品主图
- 价格展示字段
- 颜色
- 尺码
- 材质
- 风格标签
- 季节
- 版型
- 商品描述
- 商品扩展属性

不建议第一版强缓存的数据：

- 下单时最终价格
- 实时库存扣减结果
- 支付状态
- 订单状态

验收方式：

```text
第一次查商品详情：Redis 未命中，查询 MySQL，并写入 Redis
第二次查同一个商品详情：Redis 命中，不再重复组装多值属性
```

---

## 8. 用户画像缓存落地说明

目标方法：

```text
UserProfileService#getProfile(Long userId)
```

缓存 key：

```text
user:profile:{userId}
```

适合缓存的数据：

- 昵称
- 头像
- 性别
- 生日

后续可以继续扩展到：

- 身高体重
- 尺码偏好
- 风格偏好
- 颜色偏好
- 预算区间
- 不喜欢的颜色

更新画像时必须删除缓存：

```text
UserProfileService#updateProfile(Long userId, UserProfileRequest request)
```

流程：

```text
更新 MySQL
-> 删除 user:profile:{userId}
-> 下次读取重新加载
```

---

## 9. 推荐候选缓存落地说明

目标方法：

```text
ProductCatalogService#findRecommendationCandidates(RecommendationCandidateQuery query)
```

缓存 key：

```text
product:recommendation-candidates:{queryHash}
```

`queryHash` 来源：

```text
category + style + season + material + fit + budgetMax
```

注意：生成 hash 前要先把查询条件标准化，例如 null 和空字符串要有统一处理方式，否则同一个语义可能生成多个 key。

推荐候选缓存 TTL 不要太长，第一版建议 10 分钟。

不建议第一版做精确删除。因为推荐候选是组合条件查询，一个商品修改可能影响很多组合 key，精确删除成本高。短 TTL 更简单可靠。

---

## 10. AI 聊天接口限流落地说明

目标方法：

```text
AssistantService#chat(Long userId, AssistantChatRequest request)
AssistantService#streamChat(Long userId, AssistantChatRequest request)
```

限流 key：

```text
assistant:rate-limit:user:{userId}:{minute}
```

第一版规则建议：

```text
同一个用户每分钟最多 10 次 AI 请求
```

Redis 操作：

```text
INCR assistant:rate-limit:user:{userId}:{minute}
第一次创建 key 时设置 EXPIRE 60 秒
如果计数超过阈值，拒绝请求
```

返回行为：

```text
同步 chat：直接抛出业务异常，前端展示“请求太频繁”
流式 streamChat：在调用 Python 前返回限流错误事件或直接抛出业务异常
```

限流必须发生在调用 Python AI 服务之前，因为它的目标是保护模型调用成本和 Python 服务。

---

## 11. 小白执行提示

每一步只做一件事：

1. 先改 `docker-compose.yml`，确认 Redis 能启动。
2. 再改 `pom.xml`，确认 Maven 能下载 Redis 依赖。
3. 再改 `application.properties`，确认 Spring Boot 能读到 Redis 配置。
4. 再写 `CacheKeyConstants`，只负责 key。
5. 再写 `CacheTtlProperties`，只负责 TTL。
6. 再写 `RedisCacheService`，只负责 Redis 读写。
7. 最后才改业务 Service。

不要一边写 Redis 工具类，一边改商品、用户、推荐和 AI 限流。一次改太多，报错时不知道是哪一步造成的。

---

## 12. 验收标准

### 手动验收

- `docker compose up -d redis` 可以启动 Redis。
- Java 后端启动时不报 Redis 连接配置错误。
- 第一次请求商品详情后，Redis 中出现 `product:detail:{spuId}`。
- 第二次请求同一个商品详情时，可以从日志或断点看到命中 Redis。
- 更新用户画像后，对应 `user:profile:{userId}` 被删除。
- 同一个推荐条件短时间内重复请求时命中推荐候选缓存。
- 同一个用户一分钟内超过限制次数调用 AI 接口时，被拒绝。

### 自动化验收

- `./mvnw test` 通过。
- `./mvnw verify` 通过。
- 新增或修改的 Java 类满足项目 Javadoc 和 Checkstyle 规则。

---

## 13. 面试表达

可以这样说：

> 我在项目里没有把 Redis 当成万能数据库，而是围绕 AI 导购链路做了四个点：商品详情缓存、用户画像缓存、推荐候选缓存和 AI 接口限流。商品详情、用户画像、推荐候选都是 AI 推荐前需要高频读取的数据，所以我用 Cache Aside 模式先查 Redis，未命中再查 MySQL，并把结果回写 Redis。数据更新时先更新 MySQL，再删除缓存，保证 MySQL 是事实源。AI 聊天接口成本更高，所以我用 Redis 计数做用户级限流，避免短时间大量请求打到 Python AI 服务。

---

## 14. 开发检查清单

- [ ] Redis 容器已加入 Docker Compose。
- [ ] Java 后端已加入 Redis starter。
- [ ] 本地配置已加入 Redis host、port、timeout。
- [ ] 测试配置能在没有真实 Redis 时稳定运行，或测试中提供 Redis 替身。
- [ ] `CacheKeyConstants` 已集中管理 key。
- [ ] `CacheTtlProperties` 已集中管理 TTL。
- [ ] `RedisCacheService` 已封装 get/set/delete/increment。
- [ ] 商品详情缓存已接入。
- [ ] 用户画像缓存已接入。
- [ ] 推荐候选缓存已接入。
- [ ] AI 限流已接入。
- [ ] 缓存命中、缓存删除、限流超过阈值都有测试。
- [ ] 文档和面试表达已同步。
