# Recommendation ES Recall and Docker Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Elasticsearch-backed recommendation candidate recall and a reproducible Docker demo startup without changing the Java-Python candidate contract.

**Architecture:** Java remains the recommendation candidate fact boundary. Elasticsearch returns ordered SPU IDs only; Java expands those SPUs into SKU-level candidates through MySQL, hydrates live price/stock from MySQL, then sends the unchanged `candidates` payload to Python. Docker demo uses Compose to run infra plus Java, Python, frontend, and a one-shot ES index rebuild.

**Tech Stack:** Java 21, Spring Boot 4.0.6, MyBatis, Redis, Elasticsearch Java Client 9.4.3, JUnit 5, Mockito, AssertJ, Docker Compose, Vite, Nginx, FastAPI.

## Global Constraints

- Do not let Python or frontend query ES directly for recommendation candidates.
- Do not change Java-Python `candidates` field names or ownership.
- ES returns SPU IDs only; MySQL remains source of truth for price, stock, status, gender, SKU facts, and checkout-sensitive data.
- Recommendation ES recall must be behind `APP_RECOMMENDATION_ES_RECALL_ENABLED`, default `false`.
- ES unavailable falls back to the existing MySQL recommendation candidate path.
- ES available but empty returns empty candidates in active mode.
- No new runtime dependency unless an existing framework cannot do the job.
- Every Java source change must follow `docs/commenting-guidelines.md`.

---

## File Structure

- `backend/src/main/java/.../product/dto/RecommendationCandidateQuery.java`
  - Add optional `recallText`, keep old constructors source-compatible.
- `backend/src/main/java/.../product/search/RecommendationEsRecallProperties.java`
  - New small properties holder for recommendation recall enablement and limit.
- `backend/src/main/java/.../product/service/RecommendationCandidateQueryService.java`
  - Select MySQL-only or ES-first recall, cache snapshots, hydrate live facts.
- `backend/src/main/java/.../product/mapper/ProductMapper.java`
  - Add Mapper method for SKU snapshots constrained by ordered SPU IDs.
- `backend/src/main/resources/mapper/product/ProductMapper.xml`
  - Add SQL that reuses the existing recommendation snapshot filters and adds `p.id in (...)`.
- `backend/src/main/java/.../assistant/service/AssistantContextService.java`
  - Fill `recallText` from the user message for chat recommendation context.
- `backend/src/test/java/.../product/RecommendationCandidateQueryServiceTests.java`
  - Service tests for ES branch, fallback, empty result, cache key, and order.
- `backend/src/test/java/.../product/ProductCatalogMapperTests.java`
  - Mapper tests for SPU-constrained recommendation snapshots.
- `backend/src/test/java/.../assistant/AssistantContextServiceTests.java`
  - Test that chat message flows into `recallText`.
- `docs/elasticsearch/recommendation-recall-evaluation.md`
  - Manual query evaluation checklist.
- `backend/Dockerfile`
  - Java runtime image.
- `../AI-Clothing-Shopping-Assistant-System/Dockerfile`
  - Python FastAPI runtime image.
- `frontend/Dockerfile`
  - Vite build + Nginx static serving.
- `frontend/nginx.conf`
  - Proxy `/api` and `/actuator` to Java backend.
- `docker-compose.demo.yml`
  - App containers and one-shot search index init.
- `.env.demo.example`
  - Java repository demo env values without real secrets.
- `scripts/start-demo.sh`
  - One-command full demo startup.
- `scripts/stop-demo.sh`
  - Stop demo stack.
- `README.md`, `observability/README.md`, `docs/elasticsearch/README.md`
  - Update run instructions and known observability scope.

---

## Task 1: Recommendation recall DTO and properties

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto/RecommendationCandidateQuery.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/search/RecommendationEsRecallProperties.java`
- Modify: `backend/src/main/resources/application.properties`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/RecommendationCandidateQueryServiceTests.java`

**Interfaces:**
- Produces: `RecommendationCandidateQuery#getRecallText()`
- Produces: `RecommendationEsRecallProperties#isEnabled()`
- Produces: `RecommendationEsRecallProperties#getLimit()`

- [ ] Write a failing service test that proves a `RecommendationCandidateQuery` can carry `recallText` and that recall is disabled by default.
- [ ] Run `cd backend && sh ./mvnw -q -Dtest=RecommendationCandidateQueryServiceTests test`; expected failure is missing constructor/property support.
- [ ] Add `recallText` to `RecommendationCandidateQuery`, preserving existing seven- and six-argument constructors.
- [ ] Add `RecommendationEsRecallProperties` with `enabled=false` and bounded `limit`, no custom validation framework.
- [ ] Add `app.recommendation.es-recall.enabled=${APP_RECOMMENDATION_ES_RECALL_ENABLED:false}` and `app.recommendation.es-recall.limit=${APP_RECOMMENDATION_ES_RECALL_LIMIT:200}`.
- [ ] Run the targeted test until it passes.
- [ ] Commit with `feat: add recommendation recall configuration`.

## Task 2: SPU-constrained recommendation snapshot query

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/mapper/ProductMapper.java`
- Modify: `backend/src/main/resources/mapper/product/ProductMapper.xml`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`

**Interfaces:**
- Consumes: `RecommendationCandidateQuery`
- Produces: `ProductMapper#findRecommendationCandidateSnapshotsBySpuIds(RecommendationCandidateQuery query, List<Long> spuIds)`

- [ ] Add a failing Mapper test that passes `[1002, 1001]` and verifies only those SPUs are returned after existing hard filters.
- [ ] Add a failing Mapper test that an empty SPU list returns no snapshots without invalid SQL.
- [ ] Run `cd backend && sh ./mvnw -q -Dtest=ProductCatalogMapperTests test`; expected failure is missing Mapper method/XML.
- [ ] Add the Mapper interface method with `@Param("query")` and `@Param("spuIds")`.
- [ ] Add a new XML select by reusing the existing recommendation snapshot projection and filters, plus guarded `p.id in (...)`.
- [ ] Run the Mapper test until it passes.
- [ ] Commit with `feat: query recommendation snapshots by spu ids`.

## Task 3: ES-first recommendation candidate service

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/RecommendationCandidateQueryService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/RecommendationCandidateQueryServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

**Interfaces:**
- Consumes: `ProductSearchGateway#search(ProductSearchCriteria criteria)`
- Consumes: `RecommendationEsRecallProperties`
- Produces: `RecommendationCandidateQueryService#findCandidates(RecommendationCandidateQuery query)` with ES-first branch

- [ ] Add failing tests for: disabled uses MySQL; enabled uses ES IDs; ES unavailable falls back MySQL; ES empty returns empty; cache key changes with different `recallText`; ES SPU order is preserved before SKU sorting.
- [ ] Run `cd backend && sh ./mvnw -q -Dtest=RecommendationCandidateQueryServiceTests test`; expected failures are missing constructor/branch behavior.
- [ ] Inject `ObjectProvider<ProductSearchGateway>` or a nullable primary gateway only through Spring, keeping test constructors simple.
- [ ] Build `ProductSearchCriteria` from `recallText`, normalized category, and configured limit.
- [ ] Call `findRecommendationCandidateSnapshotsBySpuIds` for ES hits.
- [ ] Catch only `ProductSearchUnavailableException` for MySQL fallback; let programming/query errors surface.
- [ ] Add `recallText` to the static snapshot cache key.
- [ ] Reorder SPU groups using the ES ID order before live hydration.
- [ ] Run service tests and affected `ProductCatalogServiceTests` until they pass.
- [ ] Commit with `feat: route recommendation candidates through es recall`.

## Task 4: Chat context recall text propagation

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java`

**Interfaces:**
- Consumes: `AssistantChatRequest#message()`
- Produces: `RecommendationCandidateQuery#recallText`

- [ ] Add a failing assistant context test proving the raw user message is passed as `recallText`.
- [ ] Run `cd backend && sh ./mvnw -q -Dtest=AssistantContextServiceTests test`; expected failure is null recall text.
- [ ] Set `recallText` in the constructed `RecommendationCandidateQuery` from `request.message()`.
- [ ] Keep hard filters unchanged and do not add Python contract fields.
- [ ] Run assistant context and Java shared contract tests.
- [ ] Commit with `feat: pass chat text into recommendation recall`.

## Task 5: Recommendation recall metrics and evaluation notes

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/RecommendationCandidateQueryService.java`
- Create: `docs/elasticsearch/recommendation-recall-evaluation.md`

**Interfaces:**
- Produces metric counters under `app.recommendation.recall.requests`
- Produces metric summaries under `app.recommendation.recall.spu.hits` and `app.recommendation.recall.candidates`

- [ ] Add failing metric assertions to `RecommendationCandidateQueryServiceTests`.
- [ ] Run the targeted test and confirm the metrics are missing.
- [ ] Add bounded metric helpers for `engine=mysql|elasticsearch` and `outcome=success|fallback|empty|disabled|unavailable|error`.
- [ ] Record SPU hits and final candidate counts without user text labels.
- [ ] Add the eight-query manual evaluation doc from the spec.
- [ ] Run service tests.
- [ ] Commit with `feat: observe recommendation recall`.

## Task 6: Docker demo runtime

**Files:**
- Create: `backend/Dockerfile`
- Create: `../AI-Clothing-Shopping-Assistant-System/Dockerfile`
- Create: `frontend/Dockerfile`
- Create: `frontend/nginx.conf`
- Create: `docker-compose.demo.yml`
- Create: `.env.demo.example`
- Create: `scripts/start-demo.sh`
- Create: `scripts/stop-demo.sh`

**Interfaces:**
- Produces: `sh scripts/start-demo.sh`
- Produces: `sh scripts/stop-demo.sh`
- Produces: `docker compose -f docker-compose.yml -f docker-compose.demo.yml up`

- [ ] Add Dockerfiles using existing Maven, npm, and Python dependency files.
- [ ] Add `docker-compose.demo.yml` with `backend-web`, `backend-worker`, `python-ai`, `frontend`, and `search-index-init`.
- [ ] Use Compose service names instead of `localhost` inside containers.
- [ ] Add healthchecks for Python and Java readiness.
- [ ] Add startup/stop scripts that run from the Java project root and hide the long Compose command.
- [ ] Run `docker compose -f docker-compose.yml -f docker-compose.demo.yml config` from the Java project; expected exit 0.
- [ ] Run shell syntax checks on the scripts.
- [ ] Commit with `feat: add docker demo stack`.

## Task 7: Documentation closure and final verification

**Files:**
- Modify: `README.md`
- Modify: `docs/elasticsearch/README.md`
- Modify: `observability/README.md`
- Modify: `docs/superpowers/specs/2026-08-07-recommendation-es-recall-and-one-click-docker.md`

**Interfaces:**
- Documents final usage and known limits.

- [ ] Update root README with one-click demo commands and manual fallback commands.
- [ ] Update ES README to mention demo stack index init.
- [ ] Fix observability README so it does not claim missing Prometheus/Grafana services are in the default Compose file.
- [ ] Mark the spec as implemented with links to the plan.
- [ ] Run targeted Java tests from Tasks 1-5.
- [ ] Run `cd backend && sh ./mvnw verify` if local runtime budget allows; otherwise report the exact blocking condition.
- [ ] Run `docker compose -f docker-compose.yml -f docker-compose.demo.yml config`.
- [ ] Attempt Docker image build; if Docker Hub base image metadata cannot be resolved, report the exact network failure and rerun in a network with registry access.
- [ ] Run `git diff --check`.
- [ ] Commit with `docs: document recommendation recall and docker demo`.
