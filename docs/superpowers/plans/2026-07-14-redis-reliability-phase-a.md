# Redis Reliability Phase A Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Redis AI 限流具备原子 TTL 语义，并让推荐候选缓存只保存静态匹配快照、每次从 MySQL 批量补齐实时价格和库存。

**Architecture:** Redis 继续只是加速与保护层。限流通过单个 Lua 脚本原子执行 `INCR + PEXPIRE`；推荐候选缓存保存不含价格和库存的静态快照，返回 Java/Python 前必须通过批量 SQL 获取实时事实、过滤不可售 SKU 并组装完整候选。

**Tech Stack:** Java 21, Spring Boot 4.0.6, Spring Data Redis, MyBatis, Redis 7.2, Testcontainers, JUnit 5, Mockito, AssertJ

---

## File Map

### Create

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidateSnapshot.java`
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/model/RecommendationCandidateLiveFact.java`
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/RedisCacheServiceIntegrationTests.java`

### Modify

- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/RedisCacheService.java`
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- `backend/src/main/resources/mapper/product/ProductMapper.xml`
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/cache/RedisCacheServiceTests.java`
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`
- `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`
- `docs/superpowers/specs/2026-07-14-java-engineering-architecture-polish-design.md`

## Task 1: Redis Lua Atomic Counter

- [ ] Add a failing unit test that captures `StringRedisTemplate.execute(...)` and verifies the limiter no longer issues a separate `expire` command.
- [ ] Run `.\mvnw.cmd -Dtest=RedisCacheServiceTests test` and confirm RED.
- [ ] Add a static `DefaultRedisScript<Long>` using:
  ```lua
  local count = redis.call('INCR', KEYS[1])
  if count == 1 then
      redis.call('PEXPIRE', KEYS[1], ARGV[1])
  end
  return count
  ```
- [ ] Validate key and positive TTL before execution; preserve `Optional.empty()` fail-open behavior on Redis errors.
- [ ] Run the focused test and confirm GREEN.
- [ ] Commit as `fix: make Redis rate limit counter atomic`.

## Task 2: Real Redis Integration Test

- [ ] Add a Testcontainers Redis 7.2 test with `@Testcontainers(disabledWithoutDocker = true)`.
- [ ] Verify the script returns 1 then 2 and the key TTL remains positive.
- [ ] Send concurrent increments and assert all returned counts are unique and the key still has TTL.
- [ ] Run `.\mvnw.cmd -Dtest=RedisCacheServiceIntegrationTests test`; when Docker is unavailable, record the explicit skip rather than claiming a real container pass.
- [ ] Commit as `test: cover Redis Lua counter semantics`.

## Task 3: Static Candidate Snapshot and Live Hydration

- [ ] Replace the old cache-hit test with a failing test proving cached static snapshots still trigger one batch live-fact query.
- [ ] Add failing tests proving zero-stock, off-sale, missing-fact, and over-budget SKUs are removed; live price and stock overwrite cached values.
- [ ] Create `RecommendationCandidateSnapshot` containing only IDs, codes, display attributes and tags.
- [ ] Create `RecommendationCandidateLiveFact` containing SKU ID, current sale price, available stock, SPU aggregate price range and total available stock.
- [ ] Add mapper methods:
  ```java
  List<RecommendationCandidateSnapshot> findRecommendationCandidateSnapshots(RecommendationCandidateQuery query);
  List<RecommendationCandidateLiveFact> findRecommendationCandidateLiveFacts(List<Long> skuIds);
  ```
- [ ] Implement cache-aside for snapshots, one batch fact query per request, Java-side live-stock/budget filtering, and deterministic stock/price/ID ordering.
- [ ] Run `.\mvnw.cmd -Dtest=ProductCatalogServiceTests test` and confirm GREEN.
- [ ] Commit as `fix: hydrate recommendation candidates from live facts`.

## Task 4: Mapper Integration and Verification

- [ ] Add H2/MyBatis integration tests proving snapshot selection does not depend on current stock while live facts return current price and stock.
- [ ] Run `.\mvnw.cmd "-Dtest=ProductCatalogServiceTests,ProductCatalogMapperTests,AssistantRateLimitServiceTests,RedisCacheServiceTests" test`.
- [ ] Run Assistant-scope and product/cache Checkstyle.
- [ ] Run full `.\mvnw.cmd verify` and report any remaining learning-demo-only violations separately.
- [ ] Update the architecture design status and Mermaid graph; do not claim the Redis container test passed if Docker was unavailable.
- [ ] Commit as `docs: record Redis reliability phase A`.

## Definition of Done

- `INCR + TTL` is one Redis atomic operation.
- No successful limiter key can be created without TTL.
- Redis failure continues to fail open for AI access.
- Cached recommendation data contains no price or inventory fact.
- Every recommendation response uses one fresh MySQL batch query for price, sale status and stock.
- Unavailable or over-budget SKUs cannot reach Python.
- Focused tests pass and repository-level quality status is reported accurately.

