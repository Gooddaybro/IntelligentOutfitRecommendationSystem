# 推荐转化归因 Phase E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将推荐曝光、点击、收藏、加购、下单和支付绑定到服务端已持久化且属于当前用户的 recommendationId，并交付可直接 provisioning 的核心商城和 AI 导购 Dashboard。

**Architecture:** V18 给行为事件增加 recommendation_id 外键。公开交互严格验证“用户 + 推荐 + 最终选中商品”；交易埋点保持 best-effort，并从加购→订单→支付逐级继承归因 ID。Micrometer 仅记录固定 funnel stage，Grafana 通过两个版本化 JSON Dashboard 消费 Prometheus 指标。

**Tech Stack:** Java 21、Spring、MyBatis、Flyway、Micrometer、Prometheus、Grafana、JUnit 5、Mockito

---

### Task 1: 行为归因可信边界

**Files:**
- Create: `backend/src/main/resources/db/migration/V18__behavior_recommendation_attribution.sql`
- Modify: `backend/src/main/java/.../behavior/dto/BehaviorEventRequest.java`
- Modify: `backend/src/main/java/.../behavior/model/BehaviorEvent.java`
- Modify: `backend/src/main/java/.../behavior/service/BehaviorEventCommand.java`
- Modify: `backend/src/main/java/.../behavior/service/BehaviorEventService.java`
- Modify: `backend/src/main/java/.../behavior/mapper/BehaviorMapper.java`
- Modify: `backend/src/main/resources/mapper/behavior/BehaviorMapper.xml`
- Modify: `backend/src/main/java/.../behavior/mapper/RecommendationAttributionMapper.java`
- Modify: `backend/src/main/resources/mapper/behavior/RecommendationAttributionMapper.xml`
- Modify: `backend/src/test/java/.../behavior/BehaviorEventServiceTests.java`

- [x] 测试公开交互拒绝其他用户、非最终推荐 SKU 和不存在的 recommendationId。
- [x] 测试合法交互保存 recommendationId。
- [x] 测试订单从近期加购继承 ID、支付从订单事件继承 ID。
- [x] 实现 V18、Mapper 查询和严格/尽力而为两种写入边界。

### Task 2: 商业入口传播

**Files:**
- Modify: `backend/src/main/java/.../cart/dto/AddCartItemRequest.java`
- Modify: `backend/src/main/java/.../cart/api/CartController.java`
- Modify: `backend/src/main/java/.../cart/service/CartService.java`
- Modify: `backend/src/main/java/.../favorite/api/FavoriteController.java`
- Modify: `backend/src/main/java/.../favorite/service/FavoriteService.java`
- Modify: `backend/src/main/java/.../order/dto/BuyNowRequest.java`
- Modify: `backend/src/main/java/.../order/service/OrderService.java`
- Modify relevant service tests

- [x] 测试加购、收藏和立即购买将可选 recommendationId 传给行为事件。
- [x] 保留旧方法/构造器兼容，不允许 recommendationId 影响价格、库存、订单或支付事实。
- [x] 运行商业服务聚焦测试。

### Task 3: 漏斗指标和 Dashboard

**Files:**
- Modify: `backend/src/main/java/.../common/observability/ApplicationMetrics.java`
- Modify: `backend/src/test/java/.../common/observability/ApplicationMetricsTests.java`
- Create: `observability/prometheus.yml`
- Create: `observability/grafana/provisioning/datasources/prometheus.yml`
- Create: `observability/grafana/provisioning/dashboards/dashboards.yml`
- Create: `observability/grafana/dashboards/java-commerce-core.json`
- Create: `observability/grafana/dashboards/ai-shopping-assistant.json`
- Modify: `docker-compose.yml`

- [x] 测试 exposure/click/favorite/cart/order/payment 固定 stage 指标，未知值归入 other。
- [x] 在成功持久化行为后记录对应 stage，不把 recommendationId 放进标签。
- [x] 提供两个自动 provisioning Dashboard 和 Prometheus scrape 配置。
- [x] 校验 JSON/YAML 可解析。

### Task 4: 第三周收口、提交和推送

- [x] 更新 7.14 文档、可观测性文档、契约适配文档和第三周完成状态。
- [x] 运行聚焦测试、`backend\\mvnw.cmd verify`、前端相关测试和 `git diff --check`。
- [x] 检查无 Secret、无未完成计划、无意外生成文件。
- [x] 使用中文提交信息提交第三周全部修改。
- [x] 与远端 `learn` 同步并推送 `origin/learn`。
