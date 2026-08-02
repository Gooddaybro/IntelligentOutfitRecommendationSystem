# Multi-Turn Intent Lifecycle Recommendation Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the polluting list-union demand state with provenance-aware constraints, enforce the Java/Python hard-soft contract, introduce unambiguous recommendation states, and guarantee that browse fallback candidates remain visible.

**Architecture:** Java owns turn parsing, constraint lifecycle, hard filtering, role validation, and final status. Python consumes Java's v3 normalized constraints, uses soft preferences only for ranking, proposes outfit roles and returns rejection diagnostics; Java validates the result before publishing it. The frontend renders only the Java status and uses a plain grid for browse fallback.

**Tech Stack:** Java 21, Spring Boot 4, MyBatis, Jackson, JUnit 5, Mockito, MySQL/H2, Python 3, FastAPI/Pydantic, unittest/pytest, React 19, TypeScript, Vitest, Testing Library, SSE.

---

## Scope and repository map

This plan implements only recommendation workstream A from the approved design. Admin SQL migration is independent and must receive a separate plan after this work is stable.

Repository roots used below:

```text
JAVA_REPO     = D:\git\推荐系统\Intelligent Outfit Recommendation System
PYTHON_REPO   = D:\git\推荐系统\AI Clothing Shopping Assistant System
CONTRACT_REPO = D:\git\推荐系统
```

Commit Java/frontend changes from `JAVA_REPO`, Python changes from `PYTHON_REPO`, and shared contract changes from `CONTRACT_REPO`. Never stage the unrelated `backend/src/test/java/.../learning/` directory.

## Target file structure

### Java intent lifecycle module

Create:

```text
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintStrength.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintOrigin.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintOperator.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/IntentConstraint.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/TurnIntent.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/EffectiveDemand.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintConflictStatus.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintConflictResult.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationStatus.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationDiagnostics.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/TurnIntentAdapter.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/IntentConstraintMerger.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DerivedConstraintResolver.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/ConstraintConflictValidator.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/LegacyDemandIntentAdapter.java
backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/OutfitRoleValidator.java
```

Responsibilities:

- `TurnIntentAdapter`: adapts the existing deterministic/LLM patch result into current-turn operations only.
- `IntentConstraintMerger`: applies scalar replacement and explicit append/remove operations without deriving values.
- `DerivedConstraintResolver`: removes orphaned derived constraints and deterministically regenerates current derived preferences.
- `ConstraintConflictValidator`: reports uncommon and unresolved combinations without deleting explicit values.
- `LegacyDemandIntentAdapter`: reads v2 snapshots once and marks unverifiable lists as `LEGACY_UNPROVENANCED`.
- `OutfitRoleValidator`: validates Python's proposed role against Java category facts.

### Python ranking module

Modify focused existing modules rather than creating a second ranking path:

```text
clothing_assistant/api/schemas.py
clothing_assistant/application/recommendation_service.py
clothing_assistant/application/answer_service.py
clothing_assistant/api/streaming.py
```

### Frontend presentation module

Modify:

```text
frontend/src/shared/api/types.ts
frontend/src/shared/api/assistantStream.ts
frontend/src/features/assistant/assistantState.ts
frontend/src/features/assistant/ChatPanel.tsx
frontend/src/pages/AiShoppingPage.tsx
```

## Task 1: Add the Java constraint value objects

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintStrength.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintOrigin.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintOperator.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/IntentConstraint.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/TurnIntent.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/EffectiveDemand.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/IntentConstraintTests.java`

- [x] **Step 1: Read the Java comment policy before adding types**

Run from `JAVA_REPO`:

```powershell
Get-Content -Raw -Encoding utf8 docs/commenting-guidelines.md
```

Expected: policy requires responsibility/boundary Javadoc on every new production type.

- [x] **Step 2: Write the failing value-object tests**

Create `IntentConstraintTests.java` with these assertions:

```java
@Test
void derivedConstraintRetainsItsParentIdentity() {
    IntentConstraint constraint = new IntentConstraint(
            "c-thermal-turn-1", "thermal", ConstraintOperator.EQUALS, List.of("WARM"),
            ConstraintStrength.SOFT, ConstraintOrigin.SYSTEM_DERIVED,
            "turn-1", "c-season-turn-1", "ACTIVE_DEMAND", null);

    assertThat(constraint.isDerived()).isTrue();
    assertThat(constraint.derivedFromConstraintId()).isEqualTo("c-season-turn-1");
}

@Test
void effectiveDemandSeparatesHardAndSoftConstraints() {
    EffectiveDemand demand = EffectiveDemand.v3(
            "日常休闲", "OUTFIT_ADVICE", List.of("OUTFIT_PLAN", "PRODUCT_SELECTION"),
            List.of(hardSeason("SUMMER")), List.of(softStyle("CASUAL")), null);

    assertThat(demand.hardFilters()).extracting(IntentConstraint::strength)
            .containsOnly(ConstraintStrength.HARD);
    assertThat(demand.softPreferences()).extracting(IntentConstraint::strength)
            .containsOnly(ConstraintStrength.SOFT);
}
```

Include private factories using IDs `c-season-turn-4` and `c-style-turn-4` so later tests reuse the same canonical values.

- [x] **Step 3: Run the focused test and confirm RED**

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=IntentConstraintTests test
```

Expected: compilation fails because the new types do not exist.

- [x] **Step 4: Implement the minimal immutable types**

Use these exact enum values:

```java
public enum ConstraintStrength { HARD, SOFT }
public enum ConstraintOrigin { USER_EXPLICIT, PROFILE, SYSTEM_DERIVED, LEGACY_UNPROVENANCED }
public enum ConstraintOperator { EQUALS, CONTAINS, MAX }
```

Implement the records with defensive list copies:

```java
public record IntentConstraint(
        String id,
        String field,
        ConstraintOperator operator,
        List<String> values,
        ConstraintStrength strength,
        ConstraintOrigin origin,
        String originTurnId,
        String derivedFromConstraintId,
        String scope,
        BigDecimal weight
) {
    public IntentConstraint {
        values = values == null ? List.of() : List.copyOf(values);
        if (id == null || id.isBlank() || field == null || field.isBlank() || values.isEmpty()) {
            throw new IllegalArgumentException("constraint id, field and values are required");
        }
        if (strength == ConstraintStrength.HARD && weight != null) {
            throw new IllegalArgumentException("hard constraints cannot carry ranking weight");
        }
    }

    public boolean isDerived() {
        return origin == ConstraintOrigin.SYSTEM_DERIVED;
    }
}
```

`TurnIntent` must contain `turnId`, `rawQuery`, scalar replacements, explicit additions, explicit removals, clear fields, request type, capabilities and measurements. `EffectiveDemand` must expose `VERSION = "demand-intent-v3"`, `hardFilters`, `softPreferences`, `subjectMeasurements`, and helper lookups by field.

- [x] **Step 5: Run the focused test and confirm GREEN**

Run the same Maven command.

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [x] **Step 6: Commit the value-object seam**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/IntentConstraintTests.java
git commit -m "重构：增加带来源的意图约束模型"
```

## Task 2: Implement merge, derived lifecycle, and conflict modules

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintConflictStatus.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/ConstraintConflictResult.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/IntentConstraintMerger.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DerivedConstraintResolver.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/ConstraintConflictValidator.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/IntentConstraintLifecycleTests.java`

- [x] **Step 1: Write failing lifecycle invariant tests**

Add tests covering all four invariants:

```java
@Test
void changingWinterToSummerRemovesOnlyWinterDerivedWarmth() {
    EffectiveDemand previous = demand(
            hard("season", "WINTER", "turn-1"),
            derived("thermal", "WARM", "turn-1", "c-season-turn-1"));
    TurnIntent turn = replaceSeason("turn-2", "SUMMER");

    EffectiveDemand merged = merger.merge(previous, turn);
    EffectiveDemand resolved = resolver.resolve(merged);

    assertThat(resolved.value("season")).contains("SUMMER");
    assertThat(resolved.softPreferences()).noneMatch(item ->
            item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
    assertThat(resolved.softPreferences()).anyMatch(item -> item.values().contains("BREATHABLE"));
}

@Test
void explicitSummerWarmthSurvivesDerivedRecalculation() {
    EffectiveDemand demand = demand(
            hard("season", "SUMMER", "turn-3"),
            explicitSoft("thermal", "WARM", "turn-3"));

    EffectiveDemand resolved = resolver.resolve(demand);

    assertThat(resolved.softPreferences()).anyMatch(item ->
            item.origin() == ConstraintOrigin.USER_EXPLICIT && item.values().contains("WARM"));
    assertThat(validator.validate(resolved).status())
            .isEqualTo(ConstraintConflictStatus.VALID_UNCOMMON_COMBINATION);
}

@Test
void currentExplicitScalarWinsOverHistoricalExplicitScalar() { /* assert FEMALE replaces MALE */ }

@Test
void mergeDoesNotApplyOneUnionRuleToEveryListField() { /* style REPLACE, scene APPEND, explicit REMOVE */ }
```

- [x] **Step 2: Run the test and confirm RED**

```powershell
cd backend
.\mvnw.cmd -Dtest=IntentConstraintLifecycleTests test
```

Expected: compilation fails because lifecycle modules do not exist.

- [x] **Step 3: Implement deterministic merge policies**

In `IntentConstraintMerger`, define the policies locally and explicitly:

```java
private static final Set<String> SCALAR_FIELDS = Set.of("targetGender", "category", "season", "budgetMax");
private static final Set<String> REPLACE_LIST_FIELDS = Set.of("style", "fitPreferences");
private static final Set<String> APPEND_LIST_FIELDS = Set.of("scene", "attributes", "thermal");
```

Apply removals before additions. Replace a scalar only when the current turn supplies it. Never create derived values here.

- [x] **Step 4: Implement parent-linked derived constraints**

In `DerivedConstraintResolver`:

```java
public EffectiveDemand resolve(EffectiveDemand demand) {
    List<IntentConstraint> surviving = demand.softPreferences().stream()
            .filter(item -> !item.isDerived() || parentStillActive(item, demand.hardFilters()))
            .collect(Collectors.toCollection(ArrayList::new));
    deriveSeasonPreferences(demand, surviving);
    return demand.withSoftPreferences(deduplicateBySemanticKey(surviving));
}
```

Support only the currently required derivations:

```text
WINTER → thermal=WARM, thickness=THICK
SUMMER → materialFeature=BREATHABLE, thickness=LIGHTWEIGHT, thermal=COOLING
```

All are `SOFT + SYSTEM_DERIVED` and reference the season constraint ID.

- [x] **Step 5: Implement conflict reporting without destructive cleanup**

Use exact statuses:

```java
public enum ConstraintConflictStatus {
    VALID,
    VALID_UNCOMMON_COMBINATION,
    RESOLVED_BY_PRIORITY,
    UNRESOLVED_HARD_CONFLICT
}
```

Return `VALID_UNCOMMON_COMBINATION` for explicit `SUMMER + WARM`. Return unresolved only when two current-turn HARD values for the same scalar survive validation.

- [x] **Step 6: Run lifecycle tests and the existing merger suite**

```powershell
.\mvnw.cmd -Dtest=IntentConstraintLifecycleTests,DemandIntentMergerTests test
```

Expected: both suites pass; no existing current-turn patch behavior regresses.

- [x] **Step 7: Commit lifecycle behavior**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/IntentConstraintLifecycleTests.java
git commit -m "功能：实现意图派生约束生命周期"
```

## Task 3: Adapt current parsers and migrate v2 snapshots safely

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/TurnIntentAdapter.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/LegacyDemandIntentAdapter.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/DemandIntentStateService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/DemandIntentStateSnapshot.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/DemandIntentStateServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`

- [x] **Step 1: Add the failing multi-turn regression**

Add a test that applies these messages to one state service:

```java
List<String> turns = List.of(
        "177 130 男性 冬天该怎么穿？",
        "女性呢？",
        "夏天呢？",
        "日常休闲"
);
```

Assert the final `EffectiveDemand`:

```java
assertThat(result.value("targetGender")).contains("FEMALE");
assertThat(result.value("season")).contains("SUMMER");
assertThat(result.values("style")).contains("CASUAL");
assertThat(result.softPreferences()).noneMatch(item ->
        item.origin() == ConstraintOrigin.SYSTEM_DERIVED && item.values().contains("WARM"));
```

Also add a legacy JSON test loading v2 `attributes:["保暖"]`; assert it becomes `LEGACY_UNPROVENANCED + SOFT` and expires after an explicit summer turn.

- [x] **Step 2: Run tests and confirm RED**

```powershell
cd backend
.\mvnw.cmd -Dtest=DemandIntentStateServiceTests,AssistantContextServiceTests test
```

Expected: the original merger retains `保暖`, so the regression fails.

- [x] **Step 3: Implement the current-turn adapter**

`TurnIntentAdapter` converts `DemandIntentPatch` into explicit operations. It must not copy fields from the previous effective state:

```java
public TurnIntent adapt(String turnId, DemandIntentPatch patch) {
    return new TurnIntent(
            turnId,
            patch.rawQuery(),
            scalarReplacements(patch),
            listAdditions(patch),
            listRemovals(patch),
            clearFields(patch),
            patch.requestType(),
            patch.requestedCapabilities(),
            patch.subjectMeasurements());
}
```

- [x] **Step 4: Implement the legacy adapter**

Convert v2 scalar hard values to constraints with their persisted source when known. Convert v2 `scene/style/fitPreferences/attributes` to `LEGACY_UNPROVENANCED + SOFT`. Do not infer `USER_EXPLICIT` from presence alone.

- [x] **Step 5: Integrate the ordered lifecycle in state service**

The only state transition path must be:

```java
TurnIntent turn = turnIntentAdapter.adapt(requestId, patch);
EffectiveDemand merged = constraintMerger.merge(previous, turn);
EffectiveDemand resolved = derivedConstraintResolver.resolve(merged);
ConstraintConflictResult conflict = conflictValidator.validate(resolved);
return persist(resolved, pendingClarification, conflict);
```

Add `ConstraintConflictResult` as a separate field on `DemandIntentStateSnapshot`; do not mix diagnostic state into `EffectiveDemand`. Keep the database columns unchanged: `effective_intent_json` carries the v3 demand, while the snapshot JSON carries the conflict result used by later decision logic.

- [x] **Step 6: Run focused state/context tests**

Expected: multi-turn regression and legacy migration tests pass.

- [x] **Step 7: Commit state integration**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant
git commit -m "修复：阻止多轮派生条件污染后续需求"
```

## Task 4: Publish and verify the v3 shared contract

**Files:**
- Modify: `D:/git/推荐系统/outfit-project-contract/contracts/java-python-chat/v1.fields.json`
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/SharedJavaPythonContractTests.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonChatRequest.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/PythonProductRef.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/MatchedDimension.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RestPythonAssistantClientTests.java`

- [x] **Step 1: Extend contract assertions before the field list**

Assert the shared field list includes:

```text
intent_constraint: id, field, operator, values, strength, origin, originTurnId,
                   derivedFromConstraintId, scope, weight
demand_intent: version, requestType, requestedCapabilities, hardFilters,
               softPreferences, subjectMeasurements
product_ref: spu_id, sku_id, reason, rank_score, matched_dimensions, outfit_role
```

Run:

```powershell
cd backend
.\mvnw.cmd -Dtest=SharedJavaPythonContractTests test
```

Expected: FAIL because the shared JSON is still v2-shaped.

- [x] **Step 2: Update the shared field list atomically**

Edit `v1.fields.json` in `CONTRACT_REPO`. Keep the outer request field `demand_intent`; replace its v3 nested field set rather than adding duplicate top-level request fields. Add `intent_constraint` and `outfit_role`.

- [x] **Step 3: Serialize EffectiveDemand to Python**

Change `PythonChatRequest.demandIntent` to `EffectiveDemand`. Extend `PythonProductRef` with:

```java
@JsonProperty("outfit_role") String outfitRole
```

Keep an overload without `outfitRole` for existing Java tests during the compatibility window.

- [x] **Step 4: Add exact JSON serialization assertions**

Assert the client request contains:

```json
"hardFilters":[{"field":"season","values":["SUMMER"],"strength":"HARD"}]
```

and that response JSON reads `"outfit_role":"TOP"`.

- [x] **Step 5: Run contract and client tests**

```powershell
.\mvnw.cmd -Dtest=SharedJavaPythonContractTests,RestPythonAssistantClientTests,PythonSseEventParserTests test
```

Expected: all pass.

- [x] **Step 6: Commit each repository separately**

From `CONTRACT_REPO`:

```powershell
git add outfit-project-contract/contracts/java-python-chat/v1.fields.json
git commit -m "契约：增加推荐约束与搭配角色字段"
```

From `JAVA_REPO`:

```powershell
git add backend/src/main backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant
git commit -m "功能：接入多轮意图 v3 契约"
```

## Task 5: Build Java candidates only from the v3 effective demand

**Files:**
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantContextService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantContext.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product/dto/RecommendationCandidateQuery.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantContextServiceTests.java`

- [x] **Step 1: Add a failing query-boundary test**

For the final effective constraints `FEMALE + SUMMER + CASUAL`, capture the `RecommendationCandidateQuery` and assert:

```java
assertThat(query.getGender()).isEqualTo("female");
assertThat(query.getSeason()).isEqualTo("summer");
assertThat(query.getStyle()).isNull();
```

The natural-language `CASUAL` preference must be sent to Python, not converted into a Java hard SQL filter. Add a separate existing-filter test showing a user-selected UI style filter remains exact.

- [x] **Step 2: Run the context tests and confirm RED**

Expected: current `detailedStyle` turns parsed style into a hard query filter.

- [x] **Step 3: Implement typed effective-demand lookups**

Construct the query only from HARD constraints:

```java
RecommendationCandidateQuery query = new RecommendationCandidateQuery(
        effectiveDemand.hardValue("category"),
        explicitUiFilter(request.style()),
        effectiveDemand.hardValue("season"),
        request.material(),
        request.fit(),
        effectiveDemand.hardInteger("budgetMax"),
        effectiveDemand.hardValue("targetGender"));
```

Delete the raw-query season fallback once all v3 paths are covered.

- [x] **Step 4: Run context and product mapper tests**

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantContextServiceTests,ProductCatalogMapperTests test
```

Expected: all pass and SQL behavior remains unchanged for explicit filters.

- [x] **Step 5: Commit the Java hard-filter boundary**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/product backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant
git commit -m "重构：仅从硬约束构建推荐候选查询"
```

## Task 6: Make Python consume HARD and SOFT without reinterpreting Java semantics

**Files:**
- Modify: `clothing_assistant/api/schemas.py`
- Modify: `clothing_assistant/application/recommendation_service.py`
- Test: `tests/test_recommendation_service.py`
- Test: `tests/test_api.py`

- [x] **Step 1: Add the failing Python regression tests**

Add exact tests:

```python
def test_soft_warm_preference_does_not_hard_reject_summer_candidates(self):
    result = build_product_rerank_result(
        candidates=[summer_casual_candidate()],
        intent_result={"intent": "recommendation"},
        user_query="日常休闲",
        user_context={},
        tool_results={},
        demand_intent=v3_demand(
            hard=[constraint("season", ["SUMMER"], "HARD")],
            soft=[constraint("thermal", ["WARM"], "SOFT")],
        ),
    )
    self.assertEqual(len(result["product_refs"]), 1)
    self.assertNotIn("HARD_FILTER_MISMATCH", result["rejected_reasons"])

def test_only_hard_constraint_can_exclude_candidate(self):
    # HARD season=WINTER excludes a summer-only candidate.
```

- [x] **Step 2: Run and confirm RED**

```powershell
python -m pytest tests/test_recommendation_service.py -q
```

Expected: the first test returns zero refs because `is_winter_warm_query` hard-rejects the candidate.

- [x] **Step 3: Add Pydantic v3 constraint schemas**

Implement:

```python
class IntentConstraint(BaseModel):
    id: str
    field: str
    operator: Literal["EQUALS", "CONTAINS", "MAX"]
    values: list[str]
    strength: Literal["HARD", "SOFT"]
    origin: Literal["USER_EXPLICIT", "PROFILE", "SYSTEM_DERIVED", "LEGACY_UNPROVENANCED"]
    originTurnId: str | None = None
    derivedFromConstraintId: str | None = None
    scope: str = "ACTIVE_DEMAND"
    weight: float | None = None
```

Update demand schema to accept v3 plus a temporary v2 compatibility branch.

- [x] **Step 4: Replace semantic re-parsing with contract lookup**

Remove `preferences_from_demand_intent` behavior that maps `保暖` back to winter. Add helpers:

```python
def hard_constraints(demand_intent): ...
def soft_preferences(demand_intent): ...
def preference_values(demand_intent, field): ...
```

Hard constraints may eliminate. Soft preferences only add or subtract ranking weight and may affect evidence/selection, but the candidate remains in `candidate_scores` and is never marked as a hard rejection.

- [x] **Step 5: Return rejection reason counts**

Return this stable shape from `build_product_rerank_result`:

```python
"rejected_reasons": {
    "HARD_FILTER_MISMATCH": hard_mismatch_count,
    "SIZE_MISMATCH": size_mismatch_count,
    "LOW_STYLE_SCORE": low_style_count,
    "MISSING_REQUIRED_EVIDENCE": missing_evidence_count,
}
```

- [x] **Step 6: Run Python recommendation and API tests**

```powershell
python -m pytest tests/test_recommendation_service.py tests/test_api.py -q
```

Expected: all tests pass, including the summer-with-explicit-warmth case.

- [x] **Step 7: Commit Python contract consumption**

```powershell
git add clothing_assistant/api/schemas.py clothing_assistant/application/recommendation_service.py tests/test_recommendation_service.py tests/test_api.py
git commit -m "fix: enforce hard and soft recommendation constraints"
```

## Task 7: Let Python propose outfit roles and keep sync/stream outputs equal

**Files:**
- Modify: `clothing_assistant/application/recommendation_service.py`
- Modify: `clothing_assistant/application/answer_service.py`
- Modify: `clothing_assistant/api/streaming.py`
- Test: `tests/test_recommendation_service.py`
- Test: `tests/test_chat_stream.py`
- Test: `tests/test_answer_service.py`

- [x] **Step 1: Write failing role and parity tests**

Assert a shirt returns `outfit_role="TOP"`, shorts return `BOTTOM`, and the same request produces identical `product_refs` in `/chat` and the final stream event.

```python
self.assertEqual(result["product_refs"][0]["outfit_role"], "TOP")
self.assertEqual(sync_result["product_refs"], stream_done["product_refs"])
```

- [x] **Step 2: Run tests and confirm RED**

```powershell
python -m pytest tests/test_recommendation_service.py tests/test_chat_stream.py tests/test_answer_service.py -q
```

Expected: `outfit_role` is missing.

- [x] **Step 3: Implement one role resolver in recommendation_service**

Use candidate category facts, not product names:

```python
ROLE_BY_CATEGORY = {
    "T恤": "TOP", "衬衫": "TOP", "卫衣": "TOP", "针织衫": "TOP",
    "外套": "OUTER", "西装": "OUTER", "羽绒服": "OUTER",
    "长裤": "BOTTOM", "休闲裤": "BOTTOM", "牛仔裤": "BOTTOM",
    "短裤": "BOTTOM", "半裙": "BOTTOM",
}
```

The role is a proposal; Java remains the validator.

- [x] **Step 4: Carry role and diagnostics through sync and stream adapters**

Both outputs must use the same agent result without recomputing roles or rejection counts.

- [x] **Step 5: Run focused Python tests**

Expected: role and sync/stream parity tests pass.

- [x] **Step 6: Commit Python composition output**

```powershell
git add clothing_assistant/application clothing_assistant/api/streaming.py tests
git commit -m "feat: return validated outfit role proposals"
```

## Task 8: Add Java role validation and five-state recommendation decisions

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationStatus.java`
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/OutfitRoleValidator.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationDecision.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantRecommendationItem.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/RecommendationDecisionService.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/RecommendationDecisionServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/OutfitRoleResolverTests.java`

- [x] **Step 1: Write failing status matrix tests**

Cover:

```text
no Java candidates                              → EMPTY
candidates, zero accepted refs                  → BROWSE_FALLBACK
accepted TOP only for OUTFIT_ADVICE             → PARTIAL_MATCH
accepted TOP + BOTTOM for OUTFIT_ADVICE         → STRONG_MATCH
accepted one item for PRODUCT_RECOMMENDATION    → STRONG_MATCH
unexpected decision exception                   → handled by caller as FAILED
```

Add a role test where Python proposes `BOTTOM` for a shirt; Java must replace it with the safe mapped role `TOP` and retain the otherwise valid item.

- [x] **Step 2: Run tests and confirm RED**

```powershell
cd backend
.\mvnw.cmd -Dtest=RecommendationDecisionServiceTests,OutfitRoleResolverTests test
```

Expected: current decision only returns `EMPTY/WEAK_FALLBACK/STRONG_MATCH` strings.

- [x] **Step 3: Implement the enum and decision matrix**

```java
public enum RecommendationStatus {
    STRONG_MATCH, PARTIAL_MATCH, BROWSE_FALLBACK, EMPTY, FAILED
}
```

Change `RecommendationDecision.recommendationStatus` to the enum. For outfit advice, evaluate accepted Java-validated roles; require `TOP + BOTTOM` for strong.

- [x] **Step 4: Validate proposed roles against Java categories**

`OutfitRoleValidator.validate(categoryName, proposedRole)` returns the proposed role only when compatible; otherwise it returns Java's safe category mapping. Unknown categories become `OTHER`.

- [x] **Step 5: Run decision and assistant service tests**

```powershell
.\mvnw.cmd -Dtest=RecommendationDecisionServiceTests,OutfitRoleResolverTests,AssistantServiceTests test
```

Expected: all pass with typed statuses.

- [x] **Step 6: Commit final decision ownership**

```powershell
git add backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant
git commit -m "功能：细分推荐状态并校验搭配角色"
```

## Task 9: Publish typed statuses and structured diagnostics in sync and SSE

**Files:**
- Create: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/RecommendationDiagnostics.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantChatResponse.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/dto/AssistantStreamDoneEvent.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/service/AssistantService.java`
- Modify: `backend/src/main/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetrics.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantControllerTests.java`
- Test: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/common/observability/ApplicationMetricsTests.java`

- [x] **Step 1: Add failing sync/SSE parity and metric tests**

Assert both paths publish the same typed status and role. Verify metrics receive:

```java
verify(metrics).recordAiSelection(24, 0, 0, RecommendationStatus.BROWSE_FALLBACK);
verify(metrics).recordAiReasonCode("PYTHON_REJECTED_ALL");
```

- [x] **Step 2: Run focused tests and confirm RED**

Expected: methods and typed diagnostic DTO do not exist.

- [x] **Step 3: Implement internal diagnostics**

```java
public record RecommendationDiagnostics(
        int javaCandidateCount,
        int pythonSelectedCount,
        int javaAcceptedCount,
        RecommendationStatus status,
        List<String> reasonCodes
) { }
```

Do not include raw queries or body measurements. Add reason codes only from this fixed set:

```text
STALE_DERIVED_CONSTRAINT_REMOVED
PYTHON_REJECTED_ALL
JAVA_DISCARDED_ALL_REFS
NO_JAVA_CANDIDATES
DEPENDENCY_FAILED
```

- [x] **Step 4: Use one result assembler for sync and stream**

Extract one private method in `AssistantService` that takes context and Python response and returns accepted items, status, recommendation ID and diagnostics. Call it from both `chat` and `ForwardingStreamHandler.onDone`.

- [x] **Step 5: Map exceptions to FAILED without stale data**

Candidate snapshot failure or unrecoverable dependency failure publishes `FAILED`; fallback with valid Java candidates publishes `BROWSE_FALLBACK`.

- [x] **Step 6: Run assistant, controller and metric tests**

```powershell
cd backend
.\mvnw.cmd -Dtest=AssistantServiceTests,AssistantControllerTests,ApplicationMetricsTests test
```

Expected: all pass; no raw user text appears in metric tags.

- [x] **Step 7: Commit diagnostics and parity**

```powershell
git add backend/src/main backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem
git commit -m "功能：统一推荐状态输出与诊断指标"
```

## Task 10: Update frontend state and guarantee fallback visibility

**Files:**
- Modify: `frontend/src/shared/api/types.ts`
- Modify: `frontend/src/shared/api/assistantStream.ts`
- Modify: `frontend/src/features/assistant/assistantState.ts`
- Modify: `frontend/src/features/assistant/ChatPanel.tsx`
- Modify: `frontend/src/pages/AiShoppingPage.tsx`
- Test: `frontend/src/shared/api/assistantStream.test.ts`
- Test: `frontend/src/features/assistant/assistantState.test.ts`
- Test: `frontend/src/pages/AiShoppingPage.test.tsx`

- [x] **Step 1: Write the failing browse fallback rendering test**

Render `OUTFIT_ADVICE + BROWSE_FALLBACK` with one candidate lacking `outfitRole`:

```tsx
expect(screen.getByText("夏季亚麻短袖衬衫")).toBeVisible();
expect(screen.queryByTestId("outfit-groups")).not.toBeInTheDocument();
expect(screen.queryByText(/AI 首选|AI 推荐/)).not.toBeInTheDocument();
```

Add `PARTIAL_MATCH` test asserting the real TOP card renders in grouped layout and the missing BOTTOM group shows text only.

- [x] **Step 2: Run and confirm RED**

```powershell
cd frontend
npm test -- --run src/pages/AiShoppingPage.test.tsx src/features/assistant/assistantState.test.ts src/shared/api/assistantStream.test.ts
```

Expected: browse fallback candidate is hidden by role filtering.

- [x] **Step 3: Update shared frontend types**

```ts
export type RecommendationStatus =
  | "STRONG_MATCH"
  | "PARTIAL_MATCH"
  | "BROWSE_FALLBACK"
  | "EMPTY"
  | "FAILED";
```

Normalize legacy `WEAK_FALLBACK` to `BROWSE_FALLBACK` and legacy `ERROR` to `FAILED` only in the SSE compatibility adapter. New state code must use only the new names.

- [x] **Step 4: Render by status, not only request type**

Use this exact decision:

```ts
const groupedOutfit = isOutfit &&
  (status === "STRONG_MATCH" || status === "PARTIAL_MATCH") &&
  recommendations.some((candidate) => candidate.outfitRole);
```

When `status === "BROWSE_FALLBACK"`, render all candidates in the ordinary product grid and pass `isAttributed={false}`.

- [x] **Step 5: Remove stale boolean state**

Delete `hasStrongMatch` from `RecommendationResultMeta`. Derive all labels and attribution eligibility from the typed status plus `recommendedItems` membership.

- [x] **Step 6: Run focused frontend tests**

Expected: browse fallback card is visible, partial grouping works, and stale SSE request protection still passes.

- [x] **Step 7: Commit frontend state semantics**

```powershell
git add frontend/src/shared/api frontend/src/features/assistant frontend/src/pages/AiShoppingPage.tsx frontend/src/pages/AiShoppingPage.test.tsx
git commit -m "修复：展示浏览降级候选并细分推荐状态"
```

## Task 11: Add cross-service regression fixtures and browser acceptance

**Files:**
- Modify: `backend/src/test/java/com/recommendation/intelligentoutfitrecommendationsystem/assistant/AssistantServiceTests.java`
- Modify: `tests/test_agent_pipeline.py`
- Modify: `frontend/e2e/ai-shopping.spec.ts`
- Modify: `frontend/e2e/fixtures/api.ts`

- [x] **Step 1: Add the shared regression fixture values**

Use the exact sequence and final candidate facts:

```text
177 130 男性 冬天该怎么穿？
女性呢？
夏天呢？
日常休闲
```

Final candidates must include one `summer + casual + in_stock` TOP and one BOTTOM. Do not use a fabricated candidate outside the Java snapshot.

- [x] **Step 2: Add Java integration assertions**

Assert final hard/soft constraints, `javaCandidateCount > 0`, no winter-derived warmth and a non-failed final status.

- [x] **Step 3: Add Python pipeline assertions**

Assert v3 input does not re-add winter, emitted refs stay in candidates, and proposed roles match candidate categories.

- [x] **Step 4: Add browser assertions**

Mock the final response first as `BROWSE_FALLBACK` with two candidates and assert both cards are visible. Add a second scenario for `PARTIAL_MATCH` with a TOP only.

- [x] **Step 5: Run the three regression layers**

Java:

```powershell
cd D:\git\推荐系统\Intelligent Outfit Recommendation System\backend
.\mvnw.cmd -Dtest=AssistantServiceTests test
```

Python:

```powershell
cd "D:\git\推荐系统\AI Clothing Shopping Assistant System"
python -m pytest tests/test_agent_pipeline.py -q
```

Frontend:

```powershell
cd "D:\git\推荐系统\Intelligent Outfit Recommendation System\frontend"
npx playwright test e2e/ai-shopping.spec.ts
```

Expected: all three layers pass.

- [x] **Step 6: Commit tests in their owning repositories**

Use `测试：覆盖多轮意图生命周期端到端回归` in Java/frontend and `test: cover multi-turn intent lifecycle regression` in Python.

## Task 12: Run full verification and update the design status

**Files:**
- Modify: `docs/superpowers/specs/2026-07-21-multi-turn-intent-lifecycle-and-admin-data-access-remediation-design.md`
- Modify only if required by verified contract behavior: `docs/contracts/java-python-chat-contract-adaptation.md`

- [x] **Step 1: Verify Java backend**

```powershell
cd D:\git\推荐系统\Intelligent Outfit Recommendation System\backend
.\mvnw.cmd verify
```

Expected: `BUILD SUCCESS`, including Checkstyle and all assistant/contract tests.

- [x] **Step 2: Verify Python service**

Use the repository's active environment, then run:

```powershell
cd "D:\git\推荐系统\AI Clothing Shopping Assistant System"
python -m pytest -q
```

Expected: all tests pass with no collection errors.

- [x] **Step 3: Verify frontend**

```powershell
cd "D:\git\推荐系统\Intelligent Outfit Recommendation System\frontend"
npm test -- --run
npm run build
npx playwright test e2e/ai-shopping.spec.ts
```

Expected: Vitest passes, TypeScript/Vite build succeeds, AI shopping E2E passes.

- [x] **Step 4: Perform a real local acceptance check**

With Java, Python, MySQL and Redis running, submit the four-turn sequence. Confirm:

```text
final gender = FEMALE
final season = SUMMER
final style includes CASUAL
no SYSTEM_DERIVED WARM from the old winter turn
javaCandidateCount > 0
renderedProductCount > 0
```

Then submit `夏天，但是办公室空调很冷，想稍微保暖`; confirm explicit WARM remains and does not empty the Java candidate snapshot.

- [x] **Step 5: Update design status with actual verification evidence**

Change the design status from “待用户评审” to “推荐工作流已实现并验证；Admin 工作流待独立计划”. Record the exact commands and outcomes, not just “tests passed”.

- [x] **Step 6: Commit documentation evidence**

```powershell
git add docs/superpowers/specs/2026-07-21-multi-turn-intent-lifecycle-and-admin-data-access-remediation-design.md docs/contracts/java-python-chat-contract-adaptation.md
git commit -m "文档：记录多轮推荐链路验证结果"
```

## Completion gate

Do not declare this plan complete unless all conditions hold:

- v3 constraints are the only new-write demand contract;
- legacy v2 lists cannot silently become user-explicit constraints;
- Python never converts SOFT warmth into a winter hard filter;
- Java owns the final five-state decision;
- sync and SSE results are assembled through one decision path;
- `BROWSE_FALLBACK` with candidates visibly renders product cards;
- the four-turn regression and explicit summer-warmth counterexample pass;
- full Java, Python and frontend verification passes;
- no Admin SQL refactor is mixed into these commits.
