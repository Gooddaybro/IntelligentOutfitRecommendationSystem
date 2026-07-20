# Elasticsearch Java Product Search C1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route the existing Java product search through Elasticsearch when enabled, hydrate ordered hits from MySQL, rebuild versioned indices from MySQL, and fall back to MySQL when Elasticsearch is unavailable.

**Architecture:** A small `ProductSearchGateway` interface has Elasticsearch and MySQL adapters. `ProductSearchService` owns fallback and MySQL hydration; `ProductSearchIndexService` owns full rebuild and atomic alias switching. Elasticsearch remains optional and MySQL remains the only source of returned commerce facts.

**Tech Stack:** Java 21, Spring Boot 4.0.6, MyBatis, Elasticsearch Java Client 9.4.3, JUnit 5, Mockito, MockMvc, Testcontainers/MySQL.

---

### Task 1: Add Optional Elasticsearch Client Configuration

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.properties`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/IntelligentOutfitRecommendationSystemApplication.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchSearchProperties.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchSearchConfiguration.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchSearchConfigurationTests.java`

- [ ] Write a context test proving the Elasticsearch client is absent by default and present when `app.elasticsearch.enabled=true`.
- [ ] Run the targeted test and verify it fails because the configuration does not exist.
- [ ] Add `elasticsearch-java:9.4.3` and `elasticsearch-rest-client:9.4.3`.
- [ ] Bind `enabled`, `uris`, alias, prefix, timeouts, limit, and bulk batch size with validated defaults.
- [ ] Create the official Java client only when enabled; configure connect/socket timeouts and close the low-level client with the Spring context.
- [ ] Add comments explaining why Elasticsearch is optional and excluded from readiness.
- [ ] Run the configuration test and commit.

### Task 2: Create the Search Seam and Fallback Orchestration

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchCriteria.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchGateway.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchUnavailableException.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/MySqlProductSearchGateway.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- Modify: `backend/src/main/resources/mapper/product/ProductMapper.xml`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`

- [ ] Write tests for primary search, allowed fallback, non-fallback propagation, ordered hydration, missing SPU removal, and empty hits.
- [ ] Run tests and verify the new types/methods are missing.
- [ ] Add the gateway interface returning ordered SPU IDs and the criteria record with a validated maximum limit.
- [ ] Implement the MySQL adapter using a dedicated ID query with the existing LIKE/category semantics.
- [ ] Add a batch hydration query that returns only on-sale products and current on-sale SKU price ranges.
- [ ] Implement `ProductSearchService` so only `ProductSearchUnavailableException` triggers fallback and hydration always comes from MySQL.
- [ ] Comment the seam, fallback boundary, and order restoration reason.
- [ ] Run service and Mapper tests and commit.

### Task 3: Implement Elasticsearch Search and Preserve Existing Cache/API Contracts

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ElasticsearchProductSearchGateway.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchGatewayConfiguration.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ElasticsearchProductSearchGatewayTests.java`

- [ ] Write tests for weighted keyword search, match-all browsing, exact filters, stable sorting, 404/5xx/transport translation, and 400 propagation.
- [ ] Verify tests fail before the adapter exists.
- [ ] Implement the Elasticsearch adapter with the approved fields and filters, returning only ordered hit IDs.
- [ ] Select Elasticsearch as primary only when enabled; otherwise select MySQL as both primary and fallback.
- [ ] Delegate cache misses in `ProductCatalogService` to `ProductSearchService` without changing cache keys or DTOs.
- [ ] Update existing service tests for the new collaborator and verify cache behavior remains intact.
- [ ] Run targeted tests and commit.

### Task 4: Build Search Documents from MySQL

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexRow.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchDocument.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- Modify: `backend/src/main/resources/mapper/product/ProductMapper.xml`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexRowTests.java`

- [ ] Write tests for material/season/style/scene aggregation, CSV normalization, duplicate removal, empty values, and approximately 40 seeded documents.
- [ ] Add one documented MyBatis query that reads all searchable product facts but excludes transaction facts.
- [ ] Map comma-separated database aggregates through `ProductSearchIndexRow` into immutable document lists.
- [ ] Derive scenes only from `场景` and `适用场景`; combine style table values and `风格` attributes deterministically.
- [ ] Run row and Mapper tests and commit.

### Task 5: Implement Versioned Full Rebuild and Internal Endpoint

**Files:**
- Move: `docs/elasticsearch/product-v1-index.json` to `backend/src/main/resources/elasticsearch/product-index.json`
- Modify: `docs/elasticsearch/README.md`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchRebuildResult.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/ProductSearchIndexService.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/api/InternalProductSearchController.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductSearchIndexServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/InternalProductSearchControllerTests.java`

- [ ] Write tests proving bulk/count failure keeps the old alias, success switches the alias, incomplete indices are deleted, and concurrent rebuild is rejected.
- [ ] Remove the alias block from the runtime Mapping because alias switching is owned by the rebuild module.
- [ ] Implement timestamped index creation, batched Bulk writes, refresh/count verification, and a single atomic alias update.
- [ ] Preserve old indices after success and delete only the failed new index.
- [ ] Add the internal-token-protected rebuild endpoint and a clear disabled response.
- [ ] Update lab commands to create the manual alias explicitly from the single runtime Mapping source.
- [ ] Run targeted tests and commit.

### Task 6: End-to-End Verification and Documentation Closure

**Files:**
- Modify: `docs/backend-feature-mapping.md`
- Modify: `docs/elasticsearch/README.md`

- [ ] Start Elasticsearch/Kibana and verify SmartCN and `product_v1` health.
- [ ] Run `backend\\mvnw.cmd verify` and require zero failures and zero Checkstyle violations.
- [ ] Start the Java backend with Elasticsearch enabled against the existing local MySQL/Redis environment.
- [ ] Call the rebuild endpoint with the internal token and verify a new physical index and alias count.
- [ ] Search a Chinese phrase and record the ordered result.
- [ ] Stop Elasticsearch, bypass/expire the search cache, and verify the same endpoint returns a MySQL fallback result.
- [ ] Restore Elasticsearch and confirm search recovery.
- [ ] Document the implemented state, the accepted Redis consistency window, and the excluded MQ/incremental work.
- [ ] Run `git diff --check`, verify a clean branch, and commit documentation closure.

## Improvement Backlog After C1

The implementation report must rank, but not implement, these follow-up options:

1. **C2 Outbox + RabbitMQ incremental indexing** — removes manual rebuild dependence while retaining rebuild as repair.
2. **Search cache namespace versioning** — makes alias switches immediately visible without Redis wildcard deletion.
3. **Pagination and total-hit contract** — removes the temporary 500-SPU compatibility cap.
4. **Search quality evaluation set** — records expected recall/order for representative Chinese queries.
5. **Synonyms/custom dictionary or IK comparison** — addresses terms such as `通勤` only after measured relevance gaps.
6. **Metrics and dashboards** — primary/fallback counts, latency, rebuild duration, rejected rebuilds, and indexing failures.
7. **Old-index retention policy** — keeps a bounded rollback window and deletes older indices safely.
