# 推荐快照 Phase D Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为同步和 SSE 成功推荐生成稳定 `recommendationId`，并保存 Java 候选集和最终推荐 SKU，建立后续转化归因的事实锚点。

**Architecture:** Behavior 模块拥有推荐快照表和写入服务；Assistant 只提交低耦合的候选/选中引用。父表记录请求、会话、调用模式和规则版本，子表按 SKU 保存候选及最终选中状态；响应只在快照写入成功后返回 recommendationId。

**Tech Stack:** Java 21、Spring、MyBatis、Flyway、MySQL/H2、JUnit 5、Mockito

---

### Task 1: 推荐快照深模块

**Files:**
- Create: `backend/src/main/resources/db/migration/V17__assistant_recommendation_schema.sql`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/model/RecommendationSnapshot.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/service/RecommendationRecordCommand.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/mapper/RecommendationAttributionMapper.java`
- Create: `backend/src/main/resources/mapper/behavior/RecommendationAttributionMapper.xml`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/service/RecommendationAttributionService.java`
- Create: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/behavior/RecommendationAttributionServiceTests.java`

- [x] 先测试服务生成稳定 ID、保存父记录，并把全部候选与最终选中结果合并为唯一 SKU 快照。
- [x] 运行测试，确认服务尚不存在导致 RED。
- [x] 实现最小模型、Mapper、事务服务和 V17 迁移。
- [x] 运行服务聚焦测试并确认 GREEN。

### Task 2: Assistant 同步和 SSE 接入

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`

- [x] 先测试同步结果和 SSE done 都返回 Attribution Service 生成的 recommendationId。
- [x] 运行测试，确认响应字段和依赖缺失导致 RED。
- [x] 在可信引用过滤后记录快照，并将 ID 加入兼容性响应字段。
- [x] 运行 Assistant 聚焦测试并确认 GREEN。

### Task 3: 集成验证和文档

**Files:**
- Modify: `docs/architecture/observability.md`
- Modify: `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

- [x] 用 Spring/Flyway 集成测试证明 V17 可迁移且 Assistant 响应包含 recommendationId。
- [x] 明确规则版本是 `java-rule-reranker-v1`，模型/Prompt/RAG 版本尚未由跨服务契约提供。
- [x] 运行聚焦测试。
- [x] 运行 `backend\\mvnw.cmd verify`、`git diff --check` 并记录证据。
