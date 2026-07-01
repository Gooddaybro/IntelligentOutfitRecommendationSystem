# Gender-Aware Recommendation Filter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java-side gender strong filtering so male requests only receive `male/unisex` recommendation candidates and female requests only receive `female/unisex` candidates.

**Architecture:** Reuse `product_attribute` as the product-fact source for `适用性别`. Java resolves target gender before querying candidates, applies SQL `EXISTS` filtering, and includes gender in the recommendation cache key. Python and frontend behavior stay unchanged in this first phase.

**Tech Stack:** Spring Boot, MyBatis XML, Flyway SQL migrations, JUnit 5, AssertJ, Mockito.

---

### Task 1: Add Failing Tests

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogMapperTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/product/ProductCatalogServiceTests.java`

- [ ] **Step 1: Add mapper tests for gender filtering**

Add tests that assert:

```java
var maleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "male");
var maleCandidates = mapper.findRecommendationCandidates(maleQuery);
assertThat(maleCandidates).noneMatch(candidate -> candidate.getSpuCode().contains("SKIRT"));

var femaleQuery = new RecommendationCandidateQuery(null, null, null, null, null, 400, "female");
var femaleCandidates = mapper.findRecommendationCandidates(femaleQuery);
assertThat(femaleCandidates).anyMatch(candidate -> candidate.getSpuCode().contains("SKIRT"));
```

- [ ] **Step 2: Add service tests for target gender resolution**

Add tests that assert:

```java
new AssistantChatRequest(null, "男生 显高显瘦", null, null, null, null, null, null, null)
```

produces `RecommendationCandidateQuery.gender = "male"`, and `"给女朋友买一件通勤半裙"` produces `"female"`.

- [ ] **Step 3: Add cache key test**

Assert `male` and `female` recommendation queries use different cache keys by checking two calls miss independently before caching each gender.

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest="ProductCatalogMapperTests,AssistantContextServiceTests,ProductCatalogServiceTests" test
```

Expected: compile/test failure because `gender` does not exist yet.

### Task 2: Implement Java Candidate Filtering

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto/RecommendationCandidateQuery.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatRequest.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/service/ProductCatalogService.java`
- Modify: `backend/src/main/resources/mapper/product/ProductMapper.xml`

- [ ] **Step 1: Add `gender` to request/query DTOs**

Add nullable `String gender` fields to `RecommendationCandidateQuery` and `AssistantChatRequest`.

- [ ] **Step 2: Resolve target gender in Java**

Use message signals first, then request gender, body data gender, profile gender. Return only `male`, `female`, or `null`.

- [ ] **Step 3: Add MyBatis gender filtering**

When `query.gender` is present, filter `product_attribute.attr_name = '适用性别'` with `attr_value IN (gender, 'unisex')`.

- [ ] **Step 4: Include gender in recommendation cache key**

Append normalized `gender` to `canonicalRecommendationQuery`.

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```powershell
.\mvnw.cmd -Dtest="ProductCatalogMapperTests,AssistantContextServiceTests,ProductCatalogServiceTests" test
```

Expected: all focused tests pass.

### Task 3: Seed Gender Facts

**Files:**
- Create: `backend/src/main/resources/db/migration/V13__seed_product_target_gender_attributes.sql`

- [ ] **Step 1: Add migration**

Insert `适用性别` rows for existing demo products. Mark `SKIRT_%` products as `female`, explicit male products as `male`, and generic basics as `unisex`.

- [ ] **Step 2: Run migration-backed tests**

Run:

```powershell
.\mvnw.cmd -Dtest="ProductCatalogMapperTests,DemoDataMigrationTests" test
```

Expected: migrations apply and gender filtering tests pass.

### Task 4: Final Verification

**Files:**
- No new files.

- [ ] **Step 1: Run backend verification**

Run:

```powershell
.\mvnw.cmd verify
```

Expected: Maven exits 0.

- [ ] **Step 2: Review diff**

Run:

```powershell
git diff --stat
git diff -- backend/src/main/java backend/src/main/resources backend/src/test/java docs/superpowers
```

Expected: only gender filtering implementation, tests, migration, and docs changed.
